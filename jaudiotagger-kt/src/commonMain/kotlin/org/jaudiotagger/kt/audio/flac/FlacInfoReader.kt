package org.jaudiotagger.kt.audio.flac

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo

/**
 * Reads audio properties from the STREAMINFO block.
 */
internal object FlacInfoReader {

    fun read(io: FileIo): FlacAudioProperties {
        val flacStream = FlacStreamReader(io)
        flacStream.findStream()

        var streamInfo: FlacStreamInfo? = null
        var isLastBlock = false

        // Walk all metadata blocks even after STREAMINFO is found: the audio data
        // offset (needed for bitrate) is only known once every block is skipped.
        while (!isLastBlock) {
            val header = MetadataBlockHeader.read(io)
            if (header.blockType == BlockType.STREAMINFO) {
                if (header.dataLength == 0) {
                    throw CannotReadException("FLAC StreamInfo has zero data length")
                }
                streamInfo = FlacStreamInfo(RawMetadataBlock.read(io, header).data)
                if (!streamInfo.isValid) {
                    throw CannotReadException("FLAC StreamInfo not valid")
                }
            } else {
                io.position += header.dataLength
            }
            isLastBlock = header.isLastBlock
        }

        val info = streamInfo ?: throw CannotReadException("Unable to find Flac StreamInfo")
        val audioStart = io.position
        val audioLength = io.size - audioStart

        return FlacAudioProperties(
            encodingType = info.encodingType,
            sampleRate = info.sampleRate,
            channels = info.channels,
            bitsPerSample = info.bitsPerSample,
            bitRate = computeBitrate(audioLength, info.durationSeconds),
            duration = info.durationSeconds.seconds,
            totalSamples = info.totalSamples,
            audioDataStartPosition = audioStart,
            audioDataEndPosition = io.size,
            md5 = info.md5,
        )
    }

    /** Counts metadata blocks; useful for tests and debugging. */
    fun countMetaBlocks(io: FileIo): Int {
        val flacStream = FlacStreamReader(io)
        flacStream.findStream()

        var count = 0
        var isLastBlock = false
        while (!isLastBlock) {
            val header = MetadataBlockHeader.read(io)
            io.position += header.dataLength
            isLastBlock = header.isLastBlock
            count++
        }
        return count
    }

    // matches jaudiotagger: kilobytes are truncated before multiplying by 8
    private fun computeBitrate(audioLength: Long, durationSeconds: Double): Int =
        ((audioLength / 1000) * 8 / durationSeconds).toInt()
}
