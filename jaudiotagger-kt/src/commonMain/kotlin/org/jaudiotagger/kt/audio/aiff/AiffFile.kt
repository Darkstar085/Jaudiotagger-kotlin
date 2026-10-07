package org.jaudiotagger.kt.audio.aiff

import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.int32BE
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.io.readUInt16BE
import org.jaudiotagger.kt.io.u
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2TagReader
import org.jaudiotagger.kt.tag.id3.Id3v2TagWriter

/**
 * AIFF/AIFC: big-endian IFF chunks inside a FORM container. Properties come
 * from the COMM chunk; the tag is an ID3v2 tag inside an "ID3 " chunk.
 */
internal object AiffFile {

    private const val FORM_HEADER_LENGTH = 12
    private const val CHUNK_HEADER_LENGTH = 8

    private class ChunkInfo(val id: String, val size: Long, val dataStart: Long) {
        val paddedEnd: Long get() = dataStart + size + (size % 2)
    }

    private fun checkFormHeader(io: FileIo): String {
        io.position = 0
        if (io.size < FORM_HEADER_LENGTH) throw CannotReadException("Not an AIFF file")
        val header = io.readFully(FORM_HEADER_LENGTH)
        if (header.decodeToString(0, 4) != "FORM") {
            throw CannotReadException("Not an AIFF file: incorrect signature")
        }
        val type = header.decodeToString(8, 12)
        if (type != "AIFF" && type != "AIFC") {
            throw CannotReadException("Invalid AIFF file: not AIFF or AIFC type")
        }
        return type
    }

    private inline fun forEachChunk(io: FileIo, block: (ChunkInfo) -> Unit): String {
        val type = checkFormHeader(io)
        var position = FORM_HEADER_LENGTH.toLong()
        while (position + CHUNK_HEADER_LENGTH <= io.size) {
            io.position = position
            val header = io.readFully(CHUNK_HEADER_LENGTH)
            val chunk = ChunkInfo(
                id = header.decodeToString(0, 4),
                size = header.readInt32BE(4).toUInt().toLong(),
                dataStart = position + CHUNK_HEADER_LENGTH,
            )
            if (chunk.dataStart + chunk.size > io.size) break
            block(chunk)
            if (chunk.paddedEnd <= position) break
            position = chunk.paddedEnd
        }
        return type
    }

    /** 80-bit IEEE 754 extended float, used by COMM for the sample rate. */
    private fun read80BitDouble(data: ByteArray, offset: Int): Double {
        val sign = if ((u(data[offset]) and 0x80) != 0) -1.0 else 1.0
        val exponent = ((u(data[offset]) and 0x7F) shl 8) or u(data[offset + 1])
        var mantissa = 0.0
        for (i in 0 until 8) {
            mantissa = mantissa * 256.0 + u(data[offset + 2 + i])
        }
        if (exponent == 0 && mantissa == 0.0) return 0.0
        return sign * mantissa * 2.0.pow(exponent - 16383 - 63)
    }

    private val compressionNames = mapOf(
        "NONE" to Triple("not compressed", true, false),
        "sowt" to Triple("PCM little-endian", true, false),
        "fl32" to Triple("PCM 32-bit floating point", true, false),
        "fl64" to Triple("PCM 64-bit floating point", true, false),
        "alaw" to Triple("Alaw 2:1", false, true),
        "ulaw" to Triple("µlaw 2:1", false, true),
        "ima4" to Triple("IMA 4:1", false, true),
    )

    fun readProperties(io: FileIo): AudioProperties {
        var channels = 0
        var sampleCount = 0L
        var bitsPerSample = 0
        var sampleRate = 0.0
        var encodingType = ""
        var isLossless = true
        var audioDataStart = -1L
        var audioDataEnd = -1L
        var foundCommon = false

        val type = forEachChunk(io) { chunk ->
            when (chunk.id) {
                "COMM" -> {
                    val data = io.readFully(chunk.size.toInt())
                    channels = data.readUInt16BE(0)
                    sampleCount = data.readInt32BE(2).toUInt().toLong()
                    bitsPerSample = data.readUInt16BE(6)
                    sampleRate = read80BitDouble(data, 8)
                    if (data.size > 18) {
                        val compression = data.decodeToString(18, 22)
                        val known = compressionNames[compression]
                        encodingType = known?.first ?: compression
                        isLossless = known?.second ?: false
                    } else {
                        encodingType = "not compressed"
                    }
                    foundCommon = true
                }

                "SSND" -> {
                    audioDataStart = chunk.dataStart
                    audioDataEnd = chunk.dataStart + chunk.size
                }
            }
        }

        if (!foundCommon || sampleRate <= 0) {
            throw CannotReadException("Not a valid AIFF file: missing or invalid COMM chunk")
        }

        val durationSeconds = sampleCount / sampleRate
        return AudioProperties(
            format = if (type == "AIFC") "AIF-C" else "AIF",
            encodingType = encodingType,
            sampleRate = sampleRate.toInt(),
            channels = channels,
            bitsPerSample = bitsPerSample,
            bitRate = (sampleRate * bitsPerSample * channels / 1000).toInt(),
            isVariableBitRate = false,
            isLossless = isLossless,
            duration = durationSeconds.seconds,
            totalSamples = sampleCount,
            audioDataStartPosition = audioDataStart,
            audioDataEndPosition = audioDataEnd,
        )
    }

    fun readTag(io: FileIo): Id3v2Tag? {
        var id3Offset = -1L
        forEachChunk(io) { chunk ->
            if (chunk.id == "ID3 " && chunk.size > 0) {
                id3Offset = chunk.dataStart
            }
        }
        if (id3Offset < 0) return null
        return Id3v2TagReader.read(io, id3Offset)
    }

    fun writeTag(io: FileIo, tag: Id3v2Tag) {
        removeId3Chunks(io)

        io.position = io.size
        val id3Bytes = Id3v2TagWriter.encodeStandalone(tag)
        val buffer = Buffer()
        buffer.write("ID3 ".encodeToByteArray())
        buffer.write(int32BE(id3Bytes.size))
        buffer.write(id3Bytes)
        if (id3Bytes.size % 2 != 0) buffer.writeByte(0)
        io.write(buffer.readByteArray())

        patchFormSize(io)
        io.flush()
    }

    fun deleteTag(io: FileIo) {
        removeId3Chunks(io)
        patchFormSize(io)
        io.flush()
    }

    private fun removeId3Chunks(io: FileIo) {
        val regions = mutableListOf<Pair<Long, Long>>()
        forEachChunk(io) { chunk ->
            if (chunk.id == "ID3 ") {
                regions += (chunk.dataStart - CHUNK_HEADER_LENGTH) to minOf(chunk.paddedEnd, io.size)
            }
        }
        for ((start, end) in regions.asReversed()) {
            if (end >= io.size) {
                io.truncate(start)
            } else {
                shiftDown(io, start, end)
            }
        }
    }

    private fun shiftDown(io: FileIo, start: Long, end: Long) {
        val buffer = ByteArray(1024 * 1024)
        var readPos = end
        var writePos = start
        while (readPos < io.size) {
            val chunk = minOf(buffer.size.toLong(), io.size - readPos).toInt()
            io.position = readPos
            val read = io.read(buffer, 0, chunk)
            if (read <= 0) break
            io.position = writePos
            io.write(buffer, 0, read)
            readPos += read
            writePos += read
        }
        io.truncate(writePos)
    }

    private fun patchFormSize(io: FileIo) {
        io.position = 4
        io.write(int32BE((io.size - 8).toInt()))
    }
}
