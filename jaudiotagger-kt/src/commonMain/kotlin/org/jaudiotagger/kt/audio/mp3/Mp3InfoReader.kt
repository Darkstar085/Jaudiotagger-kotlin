package org.jaudiotagger.kt.audio.mp3

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioException
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.audio.id3.Id3v2Detector
import org.jaudiotagger.kt.io.FileIo

/**
 * Finds the first MPEG audio frame (skipping a leading ID3v2 tag and any
 * garbage) and derives the audio properties, using the Xing/VBRI header for
 * exact VBR frame counts when present.
 */
internal object Mp3InfoReader {

    private const val FILE_BUFFER_SIZE = 5000
    private const val MIN_BUFFER_REMAINING_REQUIRED =
        MpegFrameHeader.HEADER_SIZE + XingFrame.MAX_BUFFER_SIZE_NEEDED

    private val channelModeNames = mapOf(
        MpegFrameHeader.MODE_STEREO to "Stereo",
        MpegFrameHeader.MODE_JOINT_STEREO to "Joint Stereo",
        MpegFrameHeader.MODE_DUAL_CHANNEL to "Dual",
        MpegFrameHeader.MODE_MONO to "Mono",
    )

    fun read(io: FileIo): Mp3AudioProperties {
        io.position = 0
        Id3v2Detector.skipId3TagIfPresent(io)
        val startByte = io.position

        var window = readWindow(io, startByte)
        var windowStart = startByte
        var index = 0

        var frameHeader: MpegFrameHeader? = null
        var xingFrame: XingFrame? = null
        var vbriFrame: VbriFrame? = null
        var headerPosition = startByte

        scan@ while (true) {
            if (window.size - index <= MIN_BUFFER_REMAINING_REQUIRED) {
                windowStart += index
                window = readWindow(io, windowStart)
                index = 0
                if (window.size <= MIN_BUFFER_REMAINING_REQUIRED) {
                    throw CannotReadException("No audio header found within the file")
                }
            }

            if (MpegFrameHeader.isMpegFrame(window, index)) {
                try {
                    val candidate = MpegFrameHeader.parse(window, index)
                    headerPosition = windowStart + index

                    val xing = XingFrame.parseIfPresent(window, index, candidate)
                    if (xing != null) {
                        frameHeader = candidate
                        xingFrame = xing
                        break@scan
                    }
                    val vbri = VbriFrame.parseIfPresent(window, index)
                    if (vbri != null) {
                        frameHeader = candidate
                        vbriFrame = vbri
                        break@scan
                    }
                    // No VBR header: confirm the sync by checking that another
                    // valid frame follows, to avoid false syncs inside tag data
                    if (isNextFrameValid(io, window, windowStart, index, candidate)) {
                        frameHeader = candidate
                        break@scan
                    }
                } catch (_: AudioException) {
                    // invalid candidate header: keep scanning
                }
            }
            index++
        }

        val header = frameHeader ?: throw CannotReadException("No audio header found within the file")
        val fileSize = io.size
        val frameLength = header.frameLength
        if (frameLength <= 0) throw CannotReadException("Invalid frame length")

        val numberOfFrames = when {
            xingFrame != null && xingFrame.isFrameCountPresent && xingFrame.frameCount > 0 ->
                xingFrame.frameCount.toLong()
            vbriFrame != null && vbriFrame.frameCount > 0 ->
                vbriFrame.frameCount.toLong()
            xingFrame != null || vbriFrame != null ->
                estimateFrameCountAfterVbrHeader(io, headerPosition, header, fileSize)
            else ->
                (fileSize - headerPosition) / frameLength
        }

        var timePerFrame = header.noOfSamples / header.samplingRate.toDouble()
        // MPEG-2/2.5 Layer III frames carry 576 samples regardless of channel mode (ISO/IEC 13818-3).
        // Layer II mono still needs Java's halving to match frame-length compensation.
        if (header.version == MpegFrameHeader.VERSION_2 || header.version == MpegFrameHeader.VERSION_2_5) {
            if (header.layer == MpegFrameHeader.LAYER_II && header.numberOfChannels == 1) {
                timePerFrame /= 2
            }
        }
        val trackLength = numberOfFrames * timePerFrame

        val isVbr = (xingFrame != null && xingFrame.isVbr) || vbriFrame != null
        val bitRate: Int = when {
            xingFrame != null && xingFrame.isVbr && xingFrame.audioSize > 0 ->
                (xingFrame.audioSize * 8 / (timePerFrame * numberOfFrames * 1000)).toInt()

            xingFrame != null && xingFrame.isVbr ->
                ((fileSize - headerPosition) * 8 / (timePerFrame * numberOfFrames * 1000)).toInt()

            vbriFrame != null && vbriFrame.audioSize > 0 ->
                (vbriFrame.audioSize * 8 / (timePerFrame * numberOfFrames * 1000)).toInt()

            vbriFrame != null ->
                ((fileSize - headerPosition) * 8 / (timePerFrame * numberOfFrames * 1000)).toInt()

            else -> header.bitRate
        }

        val encoder = xingFrame?.encoder ?: vbriFrame?.encoder ?: ""

        return Mp3AudioProperties(
            encodingType = "${header.versionAsString} ${header.layerAsString}",
            sampleRate = header.samplingRate,
            channels = header.numberOfChannels,
            bitRate = bitRate,
            isVariableBitRate = isVbr,
            duration = trackLength.seconds,
            audioDataStartPosition = headerPosition,
            audioDataEndPosition = fileSize,
            numberOfFrames = numberOfFrames,
            encoder = encoder,
            channelMode = channelModeNames.getValue(header.channelMode),
        )
    }

    private fun readWindow(io: FileIo, position: Long): ByteArray {
        val length = minOf(FILE_BUFFER_SIZE.toLong(), io.size - position).coerceAtLeast(0)
        if (length == 0L) return ByteArray(0)
        io.position = position
        val buffer = ByteArray(length.toInt())
        var read = 0
        while (read < buffer.size) {
            val n = io.read(buffer, read, buffer.size - read)
            if (n <= 0) break
            read += n
        }
        return if (read == buffer.size) buffer else buffer.copyOf(read)
    }

    /**
     * When Xing/VBRI advertises a frame count of 0 (or omits it), estimate from the first
     * MPEG frame after the VBR header frame — not from the VBR frame's own bitrate slot.
     *
     * VBR files often have a short run of transitional frames right after the Xing slot whose
     * declared bitrate differs from the stable stream that follows (e.g. 192 kbit/s then 128 kbit/s).
     * The Xing wrapper itself may use the same bitrate as the main audio, so we pick the first
     * frame whose immediate successor matches its own header rather than comparing against the
     * wrapper bitrate.
     */
    private fun estimateFrameCountAfterVbrHeader(
        io: FileIo,
        vbrFrameStart: Long,
        vbrFrame: MpegFrameHeader,
        fileSize: Long,
    ): Long {
        val audioFrame = findFirstStableAudioFrameAfterVbrHeader(io, vbrFrameStart, vbrFrame, fileSize)
            ?: return (fileSize - vbrFrameStart) / vbrFrame.frameLength
        val audioFrameStart = audioFrame.first
        val audioHeader = audioFrame.second
        return (fileSize - audioFrameStart) / audioHeader.frameLength
    }

    private fun findFirstStableAudioFrameAfterVbrHeader(
        io: FileIo,
        vbrFrameStart: Long,
        vbrFrame: MpegFrameHeader,
        fileSize: Long,
    ): Pair<Long, MpegFrameHeader>? {
        var windowStart = vbrFrameStart
        var window = readWindow(io, windowStart)
        var index = vbrFrame.frameLength
        val maxScan = maxOf(vbrFrame.frameLength * 32, 4096)
        while (index <= maxScan) {
            while (index + MpegFrameHeader.HEADER_SIZE > window.size) {
                windowStart += index
                if (windowStart >= fileSize) return null
                window = readWindow(io, windowStart)
                index = 0
            }
            if (MpegFrameHeader.isMpegFrame(window, index)) {
                try {
                    val candidate = MpegFrameHeader.parse(window, index)
                    if (candidate.frameLength > 0 &&
                        hasMatchingSuccessor(io, window, windowStart, index, candidate)
                    ) {
                        return (windowStart + index) to candidate
                    }
                } catch (_: AudioException) {
                    // keep scanning
                }
            }
            index++
        }
        return null
    }

    /** True when the frame at [index] + [header.frameLength] parses and matches [header]. */
    private fun hasMatchingSuccessor(
        io: FileIo,
        window: ByteArray,
        windowStart: Long,
        index: Int,
        header: MpegFrameHeader,
    ): Boolean {
        val nextHeader = peekFrameHeaderAfter(io, window, windowStart, index, header.frameLength)
            ?: return false
        return nextHeader.bitRate == header.bitRate &&
            nextHeader.version == header.version &&
            nextHeader.layer == header.layer &&
            nextHeader.samplingRate == header.samplingRate
    }

    private fun peekFrameHeaderAfter(
        io: FileIo,
        window: ByteArray,
        windowStart: Long,
        index: Int,
        offsetFromIndex: Int,
    ): MpegFrameHeader? {
        val frameLength = offsetFromIndex
        if (frameLength <= 0) return null

        var buffer = window
        var offset = index
        if (buffer.size - offset <= MIN_BUFFER_REMAINING_REQUIRED + frameLength) {
            buffer = readWindow(io, windowStart + index)
            offset = 0
            if (buffer.size <= MIN_BUFFER_REMAINING_REQUIRED + frameLength) return null
        }

        val next = offset + frameLength
        if (next + MpegFrameHeader.HEADER_SIZE > buffer.size) return null
        if (!MpegFrameHeader.isMpegFrame(buffer, next)) return null
        return try {
            MpegFrameHeader.parse(buffer, next)
        } catch (_: AudioException) {
            null
        }
    }

    /** Checks that a parseable frame follows the candidate frame. */
    private fun isNextFrameValid(
        io: FileIo,
        window: ByteArray,
        windowStart: Long,
        index: Int,
        header: MpegFrameHeader,
    ): Boolean {
        val frameLength = header.frameLength
        if (frameLength > FILE_BUFFER_SIZE - MIN_BUFFER_REMAINING_REQUIRED) return false
        if (frameLength <= 0) return false

        var buffer = window
        var offset = index
        if (buffer.size - offset <= MIN_BUFFER_REMAINING_REQUIRED + frameLength) {
            buffer = readWindow(io, windowStart + index)
            offset = 0
            if (buffer.size <= MIN_BUFFER_REMAINING_REQUIRED + frameLength) return false
        }

        val next = offset + frameLength
        if (next + MpegFrameHeader.HEADER_SIZE > buffer.size) return false
        if (!MpegFrameHeader.isMpegFrame(buffer, next)) return false
        return try {
            MpegFrameHeader.parse(buffer, next)
            true
        } catch (_: AudioException) {
            false
        }
    }
}
