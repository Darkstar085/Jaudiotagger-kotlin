package org.jaudiotagger.kt.audio.dsf

import kotlin.time.Duration.Companion.seconds
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.u
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2TagReader
import org.jaudiotagger.kt.tag.id3.Id3v2TagWriter
import org.jaudiotagger.kt.tag.id3.Id3v2Version

/**
 * DSF (DSD Stream File): a "DSD " header chunk pointing to an optional ID3v2
 * tag at the end of the file, plus a "fmt " chunk with the audio properties.
 */
internal object DsfFile {

    private const val DSD_HEADER_LENGTH = 28 // "DSD " + chunkSize(8) + fileLength(8) + metadataOffset(8)
    private const val FMT_HEADER_LENGTH = 12 // "fmt " + chunkSize(8)

    private class DsdChunk(val chunkSize: Long, val fileLength: Long, val metadataOffset: Long)

    private fun ByteArray.readInt64LE(offset: Int): Long =
        (readInt32LE(offset).toUInt().toLong()) or (readInt32LE(offset + 4).toLong() shl 32)

    private fun int64LE(value: Long): ByteArray = ByteArray(8) { i -> (value ushr (8 * i)).toByte() }

    private fun readDsdChunk(io: FileIo): DsdChunk {
        io.position = 0
        if (io.size < DSD_HEADER_LENGTH) throw CannotReadException("Not a valid dsf file")
        val header = io.readFully(DSD_HEADER_LENGTH)
        if (header.decodeToString(0, 4) != "DSD ") {
            throw CannotReadException("Not a valid dsf file. Content does not start with 'DSD '")
        }
        return DsdChunk(
            chunkSize = header.readInt64LE(4),
            fileLength = header.readInt64LE(12),
            metadataOffset = header.readInt64LE(20),
        )
    }

    fun readProperties(io: FileIo): AudioProperties {
        readDsdChunk(io)

        val fmtHeader = io.readFully(FMT_HEADER_LENGTH)
        if (fmtHeader.decodeToString(0, 4) != "fmt ") {
            throw CannotReadException("Not a valid dsf file. Content does not include 'fmt ' chunk")
        }
        val fmtSize = fmtHeader.readInt64LE(4)
        if (fmtSize - FMT_HEADER_LENGTH < 40) {
            throw CannotReadException("Not a valid dsf file. 'fmt ' chunk too small")
        }
        val data = io.readFully((fmtSize - FMT_HEADER_LENGTH).toInt())

        val channels = data.readInt32LE(12)
        val sampleRate = data.readInt32LE(16)
        val bitsPerSample = data.readInt32LE(20)
        val sampleCount = data.readInt64LE(24)

        return AudioProperties(
            format = "DSF",
            encodingType = "DSF",
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            bitRate = bitsPerSample * sampleRate * channels / 1000,
            isVariableBitRate = false,
            isLossless = true,
            duration = (sampleCount.toDouble() / sampleRate).seconds,
            totalSamples = sampleCount,
        )
    }

    fun readTag(io: FileIo): Id3v2Tag? {
        val dsd = readDsdChunk(io)
        if (dsd.metadataOffset <= 0 || dsd.metadataOffset >= io.size) return null
        return Id3v2TagReader.read(io, dsd.metadataOffset)
    }

    fun writeTag(io: FileIo, tag: Id3v2Tag) {
        val dsd = readDsdChunk(io)

        // the tag chunk lives at the end of the file: truncate and rewrite it
        val tagOffset = if (dsd.metadataOffset > 0) dsd.metadataOffset else io.size
        io.truncate(tagOffset)
        io.position = tagOffset

        // DSF chunks are even-aligned
        var tagBytes = Id3v2TagWriter.encodeStandalone(tag)
        if (tagBytes.size % 2 != 0) {
            tagBytes = Id3v2TagWriter.encodeStandalone(tag, tagBytes.size + 1)
        }
        io.write(tagBytes)

        updateDsdChunk(io, fileLength = io.size, metadataOffset = tagOffset)
        io.flush()
    }

    fun deleteTag(io: FileIo) {
        val dsd = readDsdChunk(io)
        if (dsd.metadataOffset <= 0) return
        io.truncate(dsd.metadataOffset)
        updateDsdChunk(io, fileLength = io.size, metadataOffset = 0)
        io.flush()
    }

    private fun updateDsdChunk(io: FileIo, fileLength: Long, metadataOffset: Long) {
        val buffer = Buffer()
        buffer.write("DSD ".encodeToByteArray())
        buffer.write(int64LE(DSD_HEADER_LENGTH.toLong()))
        buffer.write(int64LE(fileLength))
        buffer.write(int64LE(metadataOffset))
        io.position = 0
        io.write(buffer.readByteArray())
    }
}
