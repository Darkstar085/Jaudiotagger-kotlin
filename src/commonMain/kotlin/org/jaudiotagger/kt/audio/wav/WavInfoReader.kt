package org.jaudiotagger.kt.audio.wav

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.readUInt16LE

/**
 * RIFF/WAVE chunk walking shared by the properties and tag readers.
 */
internal object WavChunks {

    const val RIFF_HEADER_LENGTH = 12
    const val CHUNK_HEADER_LENGTH = 8

    class ChunkInfo(val id: String, val size: Long, val dataStart: Long) {
        /** End of chunk data including the odd-size pad byte. */
        val paddedEnd: Long get() = dataStart + size + (size % 2)
    }

    fun checkRiffHeader(io: FileIo) {
        io.position = 0
        if (io.size < RIFF_HEADER_LENGTH) throw CannotReadException("Wav RIFF Header not valid")
        val header = io.readFully(RIFF_HEADER_LENGTH)
        if (header.decodeToString(0, 4) != "RIFF" || header.decodeToString(8, 12) != "WAVE") {
            throw CannotReadException("Wav RIFF Header not valid")
        }
    }

    /** Iterates top-level chunks; [block] runs with the position at chunk data start. */
    inline fun forEachChunk(io: FileIo, block: (ChunkInfo) -> Unit) {
        checkRiffHeader(io)
        var position = RIFF_HEADER_LENGTH.toLong()
        while (position + CHUNK_HEADER_LENGTH <= io.size) {
            io.position = position
            val header = io.readFully(CHUNK_HEADER_LENGTH)
            val id = header.decodeToString(0, 4)
            val size = header.readInt32LE(4).toUInt().toLong()
            val chunk = ChunkInfo(id, size, position + CHUNK_HEADER_LENGTH)
            if (chunk.dataStart + size > io.size) break
            block(chunk)
            if (chunk.paddedEnd <= position) break
            position = chunk.paddedEnd
        }
    }

    val subFormats: Map<Int, String> = mapOf(
        0x1 to "WAV PCM",
        0x2 to "WAV ADPCM",
        0x3 to "WAV IEEE_FLOAT",
        0x6 to "WAV A-LAW",
        0x7 to "WAV µ-LAW",
        0x31 to "GSM_COMPRESSED",
        0x55 to "WAV MP3",
        0xFFFE to "EXTENSIBLE",
    )
}

/**
 * Reads audio properties from the "fmt " (and "fact"/"data") chunks.
 */
internal object WavInfoReader {

    fun read(io: FileIo): AudioProperties {
        var channels = 0
        var sampleRate = 0
        var byteRate = 0
        var bitsPerSample = 0
        var encodingType = ""
        var sampleCount: Long? = null
        var audioDataLength = -1L
        var audioDataStart = -1L
        var foundFormat = false
        var foundData = false

        WavChunks.forEachChunk(io) { chunk ->
            when (chunk.id) {
                "fmt " -> {
                    val data = io.readFully(chunk.size.toInt())
                    var formatCode = data.readUInt16LE(0)
                    channels = data.readUInt16LE(2)
                    sampleRate = data.readInt32LE(4)
                    byteRate = data.readInt32LE(8)
                    bitsPerSample = data.readUInt16LE(14)
                    // WAVE_FORMAT_EXTENSIBLE: real format code and bit depth follow
                    if (formatCode == 0xFFFE && data.size >= 40 && data.readUInt16LE(16) == 22) {
                        bitsPerSample = data.readUInt16LE(18)
                        formatCode = data.readUInt16LE(24)
                    }
                    val description = WavChunks.subFormats[formatCode]
                    encodingType = when {
                        description != null && bitsPerSample > 0 -> "$description $bitsPerSample bits"
                        description != null -> description
                        else -> "Unknown Sub Format Code:0x${formatCode.toString(16)}"
                    }
                    foundFormat = true
                }

                "fact" -> {
                    if (chunk.size >= 4) {
                        sampleCount = io.readFully(4).readInt32LE(0).toUInt().toLong()
                    }
                }

                "data" -> {
                    audioDataLength = chunk.size
                    audioDataStart = chunk.dataStart
                    foundData = true
                }
            }
        }

        if (!foundFormat || !foundData) {
            throw CannotReadException("Unable to safely read chunks for this file, appears to be corrupt")
        }

        val durationSeconds = when {
            sampleCount != null && sampleRate > 0 -> sampleCount.toDouble() / sampleRate
            audioDataLength > 0 && byteRate > 0 -> audioDataLength.toDouble() / byteRate
            else -> throw CannotReadException("Wav Data Header Missing")
        }

        return AudioProperties(
            format = "WAV",
            encodingType = encodingType,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            bitRate = byteRate * 8 / 1000,
            isVariableBitRate = false,
            isLossless = true,
            duration = durationSeconds.seconds,
            totalSamples = sampleCount,
            audioDataStartPosition = audioDataStart,
            audioDataEndPosition = if (audioDataStart >= 0) audioDataStart + audioDataLength else -1,
        )
    }
}
