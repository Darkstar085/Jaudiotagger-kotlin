package org.jaudiotagger.kt.audio.monkey

import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.readUInt16LE

/**
 * Reads audio properties from the Monkey's Audio ("MAC ") descriptor and header.
 * Only the modern (3.98+) descriptor layout is supported, as in the original.
 */
internal object MonkeyInfoReader {

    fun read(io: FileIo): AudioProperties {
        io.position = 0
        val descriptor = Descriptor.read(io) ?: throw CannotReadException("Not a Monkey's Audio file")
        val header = Header.read(io, descriptor) ?: throw CannotReadException("Invalid Monkey's Audio header")
        if (header.sampleRate <= 0) {
            throw CannotReadException("Invalid sample rate in Monkey's Audio header")
        }

        val totalSamples = header.totalSamples
        val durationSeconds = if (totalSamples > 0) totalSamples.toDouble() / header.sampleRate else 0.0
        val frameBytes = descriptor.frameBytes
        val bitRate = if (frameBytes > 0 && durationSeconds > 0) {
            (frameBytes * 8.0 / durationSeconds / 1000.0).roundToInt()
        } else 0

        return AudioProperties(
            format = "Monkey's Audio",
            encodingType = "Monkey's Audio ${descriptor.versionString}",
            sampleRate = header.sampleRate,
            channels = header.channels,
            bitsPerSample = header.bitsPerSample,
            bitRate = bitRate,
            isVariableBitRate = true,
            isLossless = true,
            duration = durationSeconds.seconds,
            totalSamples = if (totalSamples > 0) totalSamples else null,
        )
    }

    private class Descriptor(
        val version: Int,
        val headerBytes: Long,
        val seekTableBytes: Long,
        val headerDataBytes: Long,
        val frameBytes: Long,
    ) {
        val versionString: String
            get() {
                val major = version / 1000
                var minor = version % 1000
                if (minor % 10 == 0) minor /= 10
                return "$major.${minor.toString().padStart(3, '0')}"
            }

        companion object {
            private val SIGNATURE = "MAC ".encodeToByteArray()

            fun read(io: FileIo): Descriptor? {
                if (io.size < 52) return null
                val start = io.position
                val data = io.readFully(52)
                if (!data.copyOfRange(0, 4).contentEquals(SIGNATURE)) return null

                val version = data.readUInt16LE(4)
                // 2 padding bytes, then six uint32 sizes and a 16-byte MD5
                val descriptorBytes = data.readInt32LE(8).toUInt().toLong()
                val headerBytes = data.readInt32LE(12).toUInt().toLong()
                val seekTableBytes = data.readInt32LE(16).toUInt().toLong()
                val headerDataBytes = data.readInt32LE(20).toUInt().toLong()
                val frameBytesLow = data.readInt32LE(24).toUInt().toLong()
                val frameBytesHigh = data.readInt32LE(28).toUInt().toLong()

                // descriptorBytes may exceed what we parsed: skip the remainder
                if (descriptorBytes > 52) {
                    io.position = start + descriptorBytes
                } else {
                    io.position = start + 52
                }
                return Descriptor(
                    version = version,
                    headerBytes = headerBytes,
                    seekTableBytes = seekTableBytes,
                    headerDataBytes = headerDataBytes,
                    frameBytes = (frameBytesHigh shl 32) or frameBytesLow,
                )
            }
        }
    }

    private class Header(
        val blocksPerFrame: Long,
        val finalFrameBlocks: Long,
        val totalFrames: Long,
        val bitsPerSample: Int,
        val channels: Int,
        val sampleRate: Int,
    ) {
        val totalSamples: Long
            get() = when {
                totalFrames == 0L -> 0
                totalFrames == 1L -> finalFrameBlocks
                else -> (totalFrames - 1) * blocksPerFrame + finalFrameBlocks
            }

        companion object {
            fun read(io: FileIo, descriptor: Descriptor): Header? {
                // APE_HEADER (3.98+): compressionLevel uint16(0), formatFlags uint16(2),
                // blocksPerFrame uint32(4), finalFrameBlocks uint32(8), totalFrames uint32(12),
                // bitsPerSample uint16(16), channels uint16(18), sampleRate uint32(20)
                // (the fork's Java reader used uint32 for the first two fields and
                // misread every following offset; this follows the actual spec)
                if (descriptor.headerBytes < 24) return null
                val data = io.readFully(descriptor.headerBytes.toInt())
                return Header(
                    blocksPerFrame = data.readInt32LE(4).toUInt().toLong(),
                    finalFrameBlocks = data.readInt32LE(8).toUInt().toLong(),
                    totalFrames = data.readInt32LE(12).toUInt().toLong(),
                    bitsPerSample = data.readUInt16LE(16),
                    channels = data.readUInt16LE(18),
                    sampleRate = data.readInt32LE(20),
                )
            }
        }
    }
}
