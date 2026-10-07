package org.jaudiotagger.kt.audio.real

import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.io.readUInt16BE
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.generic.GenericTag
import kotlin.time.Duration.Companion.seconds

/**
 * Reads RealAudio/RealMedia (.ra/.rm) properties (PROP chunk) and metadata
 * (CONT chunk). Read-only, like the original library.
 */
internal object RealFileReader {

    private class Chunk(val id: String, val data: ByteArray)

    private fun readChunk(io: FileIo): Chunk {
        val header = io.readFully(8)
        val id = header.decodeToString(0, 4)
        val size = header.readInt32BE(4)
        if (size < 8) {
            throw CannotReadException(
                "Corrupt file: RealAudio chunk length at position ${io.position - 4} cannot be less than 8"
            )
        }
        if (size > io.size - io.position + 8) {
            throw CannotReadException(
                "Corrupt file: RealAudio chunk length of $size at position ${io.position - 4} " +
                        "extends beyond the end of the file"
            )
        }
        return Chunk(id, io.readFully(size - 8))
    }

    fun readProperties(io: FileIo): AudioProperties {
        io.position = 0
        readChunk(io) // .RMF
        var prop = readChunk(io)
        while (prop.id != "PROP") {
            prop = readChunk(io)
        }

        val data = prop.data
        val objVersion = data.readUInt16BE(0)
        var bitRate = 0
        var durationSeconds = 0
        var isVbr = false
        if (objVersion == 0) {
            val maxBitRate = data.readInt32BE(2).toUInt().toLong() / 1000
            val avgBitRate = data.readInt32BE(6).toUInt().toLong() / 1000
            durationSeconds = (data.readInt32BE(22).toUInt().toLong() / 1000).toInt()
            bitRate = avgBitRate.toInt()
            isVbr = maxBitRate != avgBitRate
        }

        return AudioProperties(
            format = "RA",
            encodingType = "RealAudio",
            sampleRate = 0,
            channels = 0,
            bitsPerSample = 0,
            bitRate = bitRate,
            isVariableBitRate = isVbr,
            isLossless = false,
            duration = durationSeconds.seconds,
        )
    }

    fun readTag(io: FileIo): GenericTag {
        io.position = 0
        readChunk(io) // .RMF
        readChunk(io) // PROP
        var chunk = readChunk(io)
        while (chunk.id != "CONT") {
            chunk = readChunk(io)
        }

        val data = chunk.data
        var pos = 0
        fun readPrefixedString(): String {
            val length = data.readUInt16BE(pos)
            pos += 2
            val value = data.decodeToString(pos, pos + length)
            pos += length
            return value
        }

        val title = readPrefixedString()
        val author = readPrefixedString()
        val copyright = readPrefixedString()
        val comment = readPrefixedString()

        // these fields are frequently off by one in the wild, hence the fallbacks
        // (matches the original implementation)
        val tag = GenericTag()
        tag.add(FieldKey.TITLE, if (title.isEmpty()) author else title)
        tag.add(FieldKey.ARTIST, if (title.isEmpty()) copyright else author)
        tag.add(FieldKey.COMMENT, comment)
        return tag
    }
}
