package org.jaudiotagger.kt.audio.mp3

import org.jaudiotagger.kt.io.readInt32BE

/**
 * Xing/Info VBR header, located inside the first MPEG frame. Carries the frame
 * count and audio size needed for accurate VBR duration and bitrate, and may be
 * followed by a LAME encoder signature.
 */
internal class XingFrame private constructor(buffer: ByteArray) {

    val isVbr: Boolean = buffer.decodeToString(0, 4) == "Xing"
    val isFrameCountPresent: Boolean
    var frameCount: Int = -1
        private set
    var audioSize: Int = -1
        private set
    var encoder: String? = null
        private set

    init {
        val flags = buffer.readInt32BE(4)
        isFrameCountPresent = (flags and 0x1) != 0
        var pos = 8
        if (isFrameCountPresent) {
            frameCount = buffer.readInt32BE(pos)
            pos += 4
        }
        if ((flags and 0x2) != 0) {
            audioSize = buffer.readInt32BE(pos)
            pos += 4
        }
        // LAME signature sits at a fixed offset past the Xing header
        if (buffer.size >= XING_HEADER_SIZE + LAME_HEADER_SIZE) {
            val id = buffer.decodeToString(XING_HEADER_SIZE, XING_HEADER_SIZE + 4)
            if (id == "LAME") {
                encoder =
                    buffer.decodeToString(XING_HEADER_SIZE, XING_HEADER_SIZE + LAME_ENCODER_SIZE)
            }
        }
    }

    companion object {
        private const val XING_HEADER_SIZE = 120
        private const val LAME_HEADER_SIZE = 36
        private const val LAME_ENCODER_SIZE = 9

        private const val MPEG_VERSION_1_MODE_MONO_OFFSET = 21
        private const val MPEG_VERSION_1_MODE_STEREO_OFFSET = 36
        private const val MPEG_VERSION_2_MODE_MONO_OFFSET = 13
        private const val MPEG_VERSION_2_MODE_STEREO_OFFSET = 21

        const val MAX_BUFFER_SIZE_NEEDED =
            MPEG_VERSION_1_MODE_STEREO_OFFSET + XING_HEADER_SIZE + LAME_HEADER_SIZE

        /**
         * Checks for a Xing/Info header inside the frame starting at [frameOffset]
         * and parses it when present.
         */
        fun parseIfPresent(
            buffer: ByteArray,
            frameOffset: Int,
            header: MpegFrameHeader
        ): XingFrame? {
            val offset = frameOffset + when {
                header.version == MpegFrameHeader.VERSION_1 &&
                        header.channelMode == MpegFrameHeader.MODE_MONO -> MPEG_VERSION_1_MODE_MONO_OFFSET

                header.version == MpegFrameHeader.VERSION_1 -> MPEG_VERSION_1_MODE_STEREO_OFFSET

                header.channelMode == MpegFrameHeader.MODE_MONO -> MPEG_VERSION_2_MODE_MONO_OFFSET

                else -> MPEG_VERSION_2_MODE_STEREO_OFFSET
            }
            if (offset + 8 > buffer.size) return null
            val id = buffer.decodeToString(offset, offset + 4)
            if (id != "Xing" && id != "Info") return null
            val end = minOf(buffer.size, offset + XING_HEADER_SIZE + LAME_HEADER_SIZE)
            return XingFrame(buffer.copyOfRange(offset, end))
        }
    }
}

/**
 * Fraunhofer VBRI header: found 32 bytes after the frame header of the first frame.
 */
internal class VbriFrame private constructor(buffer: ByteArray) {

    val frameCount: Int = buffer.readInt32BE(14)
    val audioSize: Int = buffer.readInt32BE(10)
    val encoder: String get() = "Fraunhofer"

    companion object {
        private const val VBRI_OFFSET = MpegFrameHeader.HEADER_SIZE + 32
        private const val VBRI_HEADER_SIZE = 18

        fun parseIfPresent(buffer: ByteArray, frameOffset: Int): VbriFrame? {
            val offset = frameOffset + VBRI_OFFSET
            if (offset + VBRI_HEADER_SIZE > buffer.size) return null
            if (buffer.decodeToString(offset, offset + 4) != "VBRI") return null
            return VbriFrame(buffer.copyOfRange(offset, offset + VBRI_HEADER_SIZE))
        }
    }
}
