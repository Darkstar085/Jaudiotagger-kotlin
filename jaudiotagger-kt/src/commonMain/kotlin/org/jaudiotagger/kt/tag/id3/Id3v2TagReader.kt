package org.jaudiotagger.kt.tag.id3

import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.io.readUInt24BE
import org.jaudiotagger.kt.io.u

/**
 * Reads ID3v2.2/2.3/2.4 tags from the start of a file.
 *
 * Unsupported niceties, matching what real files need: whole-tag (v2.3) and
 * per-frame (v2.4) unsynchronization are reversed, extended headers are
 * skipped; compressed and encrypted frames are preserved as [Id3v2Frame.Unknown]
 * (zlib is unavailable in common Kotlin).
 */
internal object Id3v2TagReader {

    private const val HEADER_LENGTH = 10

    fun read(io: FileIo, offset: Long = 0): Id3v2Tag? {
        io.position = offset
        if (io.size - offset < HEADER_LENGTH) return null
        val header = io.readFully(HEADER_LENGTH)
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }

        val major = u(header[3])
        val version = when (major) {
            2 -> Id3v2Version.V22
            3 -> Id3v2Version.V23
            4 -> Id3v2Version.V24
            else -> return null
        }
        val flags = u(header[5])
        val wholeTagUnsync = (flags and 0x80) != 0
        val hasExtendedHeader = (flags and 0x40) != 0

        val tagSize = synchsafeToInt(header, 6)
        if (tagSize <= 0) return null

        var body = io.readFully(minOf(tagSize.toLong(), io.size - offset - HEADER_LENGTH).toInt())
        if (wholeTagUnsync) {
            body = removeUnsynchronization(body)
        }

        var pos = 0
        if (hasExtendedHeader) {
            pos += when (version) {
                // v2.3: size field (4 bytes, not synchsafe) does not include itself
                Id3v2Version.V23 -> 4 + body.readInt32BE(0)
                // v2.4: synchsafe size includes itself
                Id3v2Version.V24 -> synchsafeToInt(body, 0)
                Id3v2Version.V22 -> 0
            }
            if (pos < 0 || pos >= body.size) return Id3v2Tag(version)
        }

        val tag = Id3v2Tag(version)
        val idLength = if (version == Id3v2Version.V22) 3 else 4
        val frameHeaderLength = if (version == Id3v2Version.V22) 6 else 10

        while (pos + frameHeaderLength <= body.size) {
            // padding or corrupt id: stop
            if (body[pos].toInt() == 0) break
            val id = body.decodeToString(pos, pos + idLength)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break

            val declaredSize: Int
            var flags1 = 0
            var flags2 = 0
            when (version) {
                Id3v2Version.V22 -> {
                    declaredSize = body.readUInt24BE(pos + 3)
                }

                Id3v2Version.V23 -> {
                    declaredSize = body.readInt32BE(pos + 4)
                    flags1 = u(body[pos + 8])
                    flags2 = u(body[pos + 9])
                }

                Id3v2Version.V24 -> {
                    declaredSize = synchsafeToInt(body, pos + 4)
                    flags1 = u(body[pos + 8])
                    flags2 = u(body[pos + 9])
                }
            }
            pos += frameHeaderLength
            when {
                declaredSize == 0 -> continue // EmptyFrameException in Java: skip, header consumed
                declaredSize < 0 -> break // InvalidFrameException
                declaredSize > body.size - pos -> break // InvalidFrameException: extends past tag
            }

            var frameData = body.copyOfRange(pos, pos + declaredSize)
            pos += declaredSize

            val compressed: Boolean
            val encrypted: Boolean
            var dropBytes = 0
            if (version == Id3v2Version.V23) {
                compressed = (flags2 and 0x80) != 0
                encrypted = (flags2 and 0x40) != 0
                if (compressed) dropBytes += 4 // decompressed size
                if (encrypted) dropBytes += 1 // encryption method
                if ((flags2 and 0x20) != 0) dropBytes += 1 // grouping id
            } else if (version == Id3v2Version.V24) {
                compressed = (flags2 and 0x08) != 0
                encrypted = (flags2 and 0x04) != 0
                if ((flags2 and 0x40) != 0) dropBytes += 1 // grouping id
                if (encrypted) dropBytes += 1
                if ((flags2 and 0x01) != 0) dropBytes += 4 // data length indicator
                if ((flags2 and 0x02) != 0) { // per-frame unsynchronization
                    frameData = removeUnsynchronization(frameData)
                }
            } else {
                compressed = false
                encrypted = false
            }

            if (dropBytes > 0 && frameData.size >= dropBytes) {
                frameData = frameData.copyOfRange(dropBytes, frameData.size)
            }

            tag.frames += if (compressed || encrypted) {
                Id3v2Frame.Unknown(id, frameData)
            } else {
                try {
                    parseFrameBody(version, id, frameData)
                } catch (_: Exception) {
                    Id3v2Frame.Unknown(id, frameData)
                }
            }
        }
        return tag
    }

    private fun parseFrameBody(version: Id3v2Version, id: String, data: ByteArray): Id3v2Frame {
        val isV22 = version == Id3v2Version.V22
        return when {
            id == "TXXX" || id == "TXX" -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val (description, next) = readNulTerminated(data, 1, encoding)
                Id3v2Frame.UserText(id, description, encoding.decode(data, next).trimNul())
            }

            id == "WXXX" || id == "WXX" -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val (description, next) = readNulTerminated(data, 1, encoding)
                Id3v2Frame.UserUrl(id, description, Id3TextEncoding.ISO_8859_1.decode(data, next).trimNul())
            }

            id == "COMM" || id == "COM" || id == "USLT" || id == "ULT" -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val language = Id3TextEncoding.ISO_8859_1.decode(data, 1, minOf(4, data.size))
                val (description, next) = readNulTerminated(data, 4, encoding)
                Id3v2Frame.LanguageText(id, language, description, encoding.decode(data, next).trimNul())
            }

            id == "APIC" && !isV22 -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val (mimeType, afterMime) = readNulTerminated(data, 1, Id3TextEncoding.ISO_8859_1)
                val pictureType = u(data[afterMime])
                val (description, afterDescription) = readNulTerminated(data, afterMime + 1, encoding)
                Id3v2Frame.Picture(id, mimeType, pictureType, description, data.copyOfRange(afterDescription, data.size))
            }

            id == "PIC" && isV22 -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val imageFormat = Id3TextEncoding.ISO_8859_1.decode(data, 1, 4)
                val pictureType = u(data[4])
                val (description, afterDescription) = readNulTerminated(data, 5, encoding)
                val mimeType = when (imageFormat.uppercase().trim()) {
                    "JPG" -> "image/jpeg"
                    "PNG" -> "image/png"
                    "GIF" -> "image/gif"
                    "BMP" -> "image/bmp"
                    else -> imageFormat
                }
                Id3v2Frame.Picture(id, mimeType, pictureType, description, data.copyOfRange(afterDescription, data.size))
            }

            id == "UFID" || id == "UFI" -> {
                val (owner, next) = readNulTerminated(data, 0, Id3TextEncoding.ISO_8859_1)
                Id3v2Frame.UniqueFileId(id, owner, data.copyOfRange(next, data.size))
            }

            id == "POPM" || id == "POP" -> {
                val (email, next) = readNulTerminated(data, 0, Id3TextEncoding.ISO_8859_1)
                val rating = if (next < data.size) u(data[next]) else 0
                var counter = 0L
                for (i in next + 1 until data.size) {
                    counter = (counter shl 8) or u(data[i]).toLong()
                }
                Id3v2Frame.Popularimeter(id, email, rating, counter)
            }

            id == "IPLS" || id == "IPL" || id == "TIPL" || id == "TMCL" -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val parts = splitNulSeparated(data, 1, encoding)
                val pairs = mutableListOf<Pair<String, String>>()
                var i = 0
                while (i + 1 < parts.size) {
                    pairs += parts[i] to parts[i + 1]
                    i += 2
                }
                Id3v2Frame.PairedText(id, pairs)
            }

            isNumberTotalFrameId(id) -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val values = splitNulSeparated(data, 1, encoding)
                Id3v2Frame.NumberTotal(id, PartOfSetValue(values.firstOrNull() ?: ""))
            }

            id.startsWith("T") -> {
                val encoding = Id3TextEncoding.fromId(u(data[0]))
                val values = splitNulSeparated(data, 1, encoding).toMutableList()
                if (values.isEmpty()) values += ""
                Id3v2Frame.Text(id, values)
            }

            id.startsWith("W") -> {
                Id3v2Frame.Url(id, Id3TextEncoding.ISO_8859_1.decode(data).trimNul())
            }

            else -> Id3v2Frame.Unknown(id, data)
        }
    }

    /** Reads a NUL-terminated string; returns the string and the index past the terminator. */
    private fun readNulTerminated(data: ByteArray, from: Int, encoding: Id3TextEncoding): Pair<String, Int> {
        val width = encoding.nulWidth
        var i = from
        while (i + width <= data.size) {
            val isNul = if (width == 2) {
                data[i].toInt() == 0 && data[i + 1].toInt() == 0
            } else {
                data[i].toInt() == 0
            }
            if (isNul) {
                return encoding.decode(data, from, i) to (i + width)
            }
            i += width
        }
        // unterminated: everything is the string
        return encoding.decode(data, from, data.size) to data.size
    }

    /** Splits the remainder of a text frame into NUL-separated values. */
    private fun splitNulSeparated(data: ByteArray, from: Int, encoding: Id3TextEncoding): List<String> {
        val values = mutableListOf<String>()
        var start = from
        while (start < data.size) {
            val (value, next) = readNulTerminated(data, start, encoding)
            if (value.isNotEmpty() || next < data.size) {
                values += value
            }
            if (next <= start) break
            start = next
        }
        return values.filter { it.isNotEmpty() }
    }

    private fun String.trimNul(): String = trimEnd(' ')

    private fun synchsafeToInt(data: ByteArray, offset: Int): Int =
        (u(data[offset]) shl 21) or (u(data[offset + 1]) shl 14) or
            (u(data[offset + 2]) shl 7) or u(data[offset + 3])

    /** Reverses unsynchronization: every $FF 00 becomes $FF. */
    private fun removeUnsynchronization(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var w = 0
        var r = 0
        while (r < data.size) {
            out[w++] = data[r]
            if (u(data[r]) == 0xFF && r + 1 < data.size && data[r + 1].toInt() == 0) {
                r += 2
            } else {
                r++
            }
        }
        return out.copyOf(w)
    }
}
