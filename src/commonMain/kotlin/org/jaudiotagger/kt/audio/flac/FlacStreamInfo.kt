package org.jaudiotagger.kt.audio.flac

import org.jaudiotagger.kt.io.readUInt16BE
import org.jaudiotagger.kt.io.readUInt24BE
import org.jaudiotagger.kt.io.toHex
import org.jaudiotagger.kt.io.u

/**
 * STREAMINFO metadata block: sample rate, channels, bit depth, sample count, MD5.
 *
 * Raw bytes are kept verbatim so the block can be rewritten unchanged.
 */
class FlacStreamInfo(val rawData: ByteArray) {

    val minBlockSize: Int = rawData.readUInt16BE(0)
    val maxBlockSize: Int = rawData.readUInt16BE(2)
    val minFrameSize: Int = rawData.readUInt24BE(4)
    val maxFrameSize: Int = rawData.readUInt24BE(7)

    /** Sample rate: 20 bits spanning bytes 10-12. */
    val sampleRate: Int =
        (u(rawData[10]) shl 12) or (u(rawData[11]) shl 4) or ((u(rawData[12]) and 0xF0) ushr 4)

    /** Channels: bits 5-7 of byte 12, stored minus one. */
    val channels: Int = ((u(rawData[12]) and 0x0E) ushr 1) + 1

    /** Bits per sample: last bit of byte 12 plus first 4 bits of byte 13, stored minus one. */
    val bitsPerSample: Int =
        ((u(rawData[12]) and 0x01) shl 4) + ((u(rawData[13]) and 0xF0) ushr 4) + 1

    /** Total inter-channel samples: 36 bits, low 4 bits of byte 13 plus bytes 14-17. */
    val totalSamples: Long =
        ((u(rawData[13]) and 0x0F).toLong() shl 32) or
            (u(rawData[14]).toLong() shl 24) or
            (u(rawData[15]).toLong() shl 16) or
            (u(rawData[16]).toLong() shl 8) or
            u(rawData[17]).toLong()

    /** MD5 of the unencoded audio, hex encoded. */
    val md5: String = if (rawData.size >= 34) rawData.toHex(18, 16) else ""

    val durationSeconds: Double = totalSamples.toDouble() / sampleRate

    val isValid: Boolean = sampleRate > 0

    val encodingType: String get() = "FLAC $bitsPerSample bits"

    override fun toString(): String =
        "SampleRate:$sampleRate Channels:$channels Bits:$bitsPerSample Samples:$totalSamples"

    companion object {
        const val STREAM_INFO_DATA_LENGTH = 34
    }
}
