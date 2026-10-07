package org.jaudiotagger.kt.audio.wavpack

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.readUInt16LE
import org.jaudiotagger.kt.io.u

/**
 * Reads audio properties from WavPack ("wvpk") block headers.
 */
internal object WavPackInfoReader {

    private const val HEADER_SIZE = 32
    private const val META_ID_WV_BITSTREAM = 0x0A
    private const val META_ID_CHANNEL_INFO = 0x1D
    private const val META_ID_SAMPLE_RATE = 0x27

    private val SAMPLE_RATES = intArrayOf(
        6000, 8000, 9600, 11025,
        12000, 16000, 22050, 24000,
        32000, 44100, 48000, 64000,
        88200, 96000, 192000, 0,
    )

    fun read(io: FileIo): AudioProperties {
        io.position = 0
        val header = locateAudioBlock(io) ?: throw CannotReadException("Unable to locate WavPack audio block")

        val metadata = readMetadata(io, header)
        val hybrid = header.isHybridMode
        val channels = if (metadata.channelCount > 0) metadata.channelCount else if (header.isMono) 1 else 2
        var bitsPerSample = header.bitsPerSample
        var sampleRate = if (metadata.sampleRate > 0) metadata.sampleRate else header.sampleRateFromFlags
        if (header.isDsd) {
            // DSD stores one bit per sample; players expect the familiar 2.8+ MHz rate
            sampleRate *= 4
            bitsPerSample = 1
        }

        val totalSamples = calculateTotalSamples(io, header)
        val durationSeconds =
            if (sampleRate > 0 && totalSamples > 0) totalSamples.toDouble() / sampleRate else 0.0

        return AudioProperties(
            format = "WavPack",
            encodingType = if (hybrid) "WavPack Hybrid" else "WavPack Lossless",
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            bitRate = 0,
            isVariableBitRate = true,
            isLossless = !hybrid,
            duration = durationSeconds.seconds,
            totalSamples = if (totalSamples > 0) totalSamples else null,
        )
    }

    private fun locateAudioBlock(io: FileIo): BlockHeader? {
        while (io.position + HEADER_SIZE <= io.size) {
            val start = io.position
            val header = BlockHeader.read(io, start) ?: return null
            if (header.blockSamples > 0) return header
            io.position = start + header.blockSize
        }
        return null
    }

    /** Total samples: first block's counter, or a walk over all block headers. */
    private fun calculateTotalSamples(io: FileIo, first: BlockHeader): Long {
        if (first.totalSamples > 0 && first.blockIndex == 0L) {
            return first.totalSamples
        }
        var samples = first.blockSamples.toLong()
        var pointer = first.blockStart + first.blockSize
        while (pointer + HEADER_SIZE <= io.size) {
            io.position = pointer
            val header = BlockHeader.read(io, pointer) ?: break
            if (header.blockSamples > 0) samples += header.blockSamples
            pointer = header.blockStart + header.blockSize
        }
        return samples
    }

    private class MetadataInfo {
        var sampleRate = 0
        var channelCount = 0
        val isComplete: Boolean get() = sampleRate > 0 && channelCount > 0
    }

    /** Walks sub-block metadata of the first audio block for non-standard rates/channels. */
    private fun readMetadata(io: FileIo, header: BlockHeader): MetadataInfo {
        val info = MetadataInfo()
        io.position = header.blockStart + HEADER_SIZE
        val metaEnd = header.blockStart + header.blockSize

        while (io.position < metaEnd) {
            val idByte = u(io.readFully(1)[0])
            val large = (idByte and 0x80) != 0
            val odd = (idByte and 0x40) != 0
            val metaId = idByte and 0x3F
            val words = if (large) {
                val b = io.readFully(3)
                u(b[0]) or (u(b[1]) shl 8) or (u(b[2]) shl 16)
            } else {
                u(io.readFully(1)[0])
            }
            val dataBytes = words * 2 + (if (odd) 1 else 0)
            val storedBytes = if (dataBytes % 2 == 0) dataBytes else dataBytes + 1

            if (metaId == META_ID_WV_BITSTREAM) break

            // corrupt sub-block length pointing past the block: stop scanning
            if (dataBytes > metaEnd - io.position) break

            if ((metaId == META_ID_SAMPLE_RATE && dataBytes >= 3) || (metaId == META_ID_CHANNEL_INFO && dataBytes >= 2)) {
                val data = io.readFully(dataBytes)
                io.position += (storedBytes - dataBytes)
                when (metaId) {
                    META_ID_SAMPLE_RATE ->
                        info.sampleRate = (u(data[2]) shl 16) or (u(data[1]) shl 8) or u(data[0])

                    META_ID_CHANNEL_INFO ->
                        info.channelCount = parseChannelCount(data)
                }
                if (info.isComplete) break
            } else {
                io.position += storedBytes
            }
        }
        return info
    }

    private fun parseChannelCount(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        var candidate = 0
        if (data.size >= 2) {
            candidate = u(data[0]) or (u(data[1]) shl 8)
            if (candidate <= 0 || candidate > 32) candidate = 0
        }
        if (candidate == 0) candidate = u(data[0])
        return if (candidate in 1..32) candidate else 0
    }

    private class BlockHeader(
        val blockStart: Long,
        val blockSize: Long,
        val flags: Int,
        val blockSamples: Int,
        val totalSamples: Long,
        val blockIndex: Long,
    ) {
        val isHybridMode: Boolean get() = (flags and 0x8) != 0
        val isMono: Boolean get() = (flags and 0x4) != 0
        val isDsd: Boolean get() = (flags and (1 shl 31)) != 0
        val bitsPerSample: Int get() = ((flags and 0x3) + 1) * 8

        val sampleRateFromFlags: Int
            get() {
                val index = (flags ushr 23) and 0xF
                return SAMPLE_RATES[index]
            }

        companion object {
            fun read(io: FileIo, start: Long): BlockHeader? {
                val buffer = io.readFully(HEADER_SIZE)
                if (buffer.decodeToString(0, 4) != "wvpk") return null

                val ckSize = buffer.readInt32LE(4).toUInt().toLong()
                val version = buffer.readUInt16LE(8)
                val blockIndexMsb = u(buffer[10])
                val totalSamplesMsb = u(buffer[11])
                val totalSamplesLow = buffer.readInt32LE(12).toUInt().toLong()
                val blockIndexLow = buffer.readInt32LE(16).toUInt().toLong()
                val blockSamples = buffer.readInt32LE(20)
                val flags = buffer.readInt32LE(24)

                val blockSize = ckSize + 8
                if (version < 0x402 || blockSize < HEADER_SIZE) return null

                return BlockHeader(
                    blockStart = start,
                    blockSize = blockSize,
                    flags = flags,
                    blockSamples = blockSamples,
                    // 40-bit counters split over an msb byte and a 32-bit low part
                    totalSamples = combineCounter(totalSamplesMsb, totalSamplesLow),
                    blockIndex = combineCounter(blockIndexMsb, blockIndexLow),
                )
            }

            private fun combineCounter(msb: Int, low: Long): Long =
                if (low == 0xFFFFFFFFL) -1 else low or (msb.toLong() shl 32)
        }
    }
}
