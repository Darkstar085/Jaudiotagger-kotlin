package org.jaudiotagger.kt.tag.id3

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.ShiftData
import org.jaudiotagger.kt.io.int32BE
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.u

/**
 * Serializes and writes ID3v2 tags at the start of a file.
 *
 * The tag is written in the version it carries ([Id3v2Tag.version]); no
 * cross-version frame conversion is attempted. When the new tag fits into the
 * existing tag area (including its padding) the audio is untouched; otherwise
 * the audio is shifted to make room, adding [DEFAULT_PADDING] for future edits.
 *
 * Text encodings on write: ISO-8859-1 when the text fits, otherwise UTF-16
 * (v2.2/v2.3) or UTF-8 (v2.4). Genres are written as plain text, not "(NN)"
 * references. Unsynchronization is never applied, like the original's default.
 */
internal object Id3v2TagWriter {

    const val DEFAULT_PADDING = 4000

    private const val HEADER_LENGTH = 10

    /** Total on-disk size (header + body + padding + footer) of an existing tag, or 0. */
    fun existingTagSize(io: FileIo): Long {
        if (io.size < HEADER_LENGTH) return 0
        io.position = 0
        val header = io.readFully(HEADER_LENGTH)
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return 0
        }
        val size =
            (u(header[6]) shl 21) or (u(header[7]) shl 14) or (u(header[8]) shl 7) or u(header[9])
        val hasFooter = (u(header[5]) and 0x10) != 0
        return HEADER_LENGTH.toLong() + size + (if (hasFooter) HEADER_LENGTH else 0)
    }

    fun write(io: FileIo, tag: Id3v2Tag) {
        val body = encodeFrames(tag)
        val oldTagSize = existingTagSize(io)
        val minimumNewSize = HEADER_LENGTH + body.size

        val totalSize: Long
        if (oldTagSize >= minimumNewSize) {
            // fits into the existing tag area: keep the file layout untouched
            totalSize = oldTagSize
        } else {
            totalSize = (minimumNewSize + DEFAULT_PADDING).toLong()
            io.position = oldTagSize
            ShiftData.shiftDataByOffsetToMakeSpace(io, (totalSize - oldTagSize).toInt())
        }

        io.position = 0
        io.write(encodeWithHeader(tag.version, body, totalSize.toInt()))
        io.flush()
    }

    /**
     * Serializes the whole tag (header, frames, zero padding up to
     * [minimumTotalSize]) as a standalone byte block, for formats that embed an
     * ID3v2 tag inside their own structure (DSF, WAV, AIFF).
     */
    fun encodeStandalone(tag: Id3v2Tag, minimumTotalSize: Int = 0): ByteArray {
        val body = encodeFrames(tag)
        val totalSize = maxOf(HEADER_LENGTH + body.size, minimumTotalSize)
        return encodeWithHeader(tag.version, body, totalSize)
    }

    private fun encodeWithHeader(
        version: Id3v2Version,
        body: ByteArray,
        totalSize: Int
    ): ByteArray {
        val declaredSize = totalSize - HEADER_LENGTH
        val out = ByteArray(totalSize) // trailing padding stays zero
        byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            version.major.toByte(), 0, // version, revision
            0, // flags: no unsync, no extended header, no footer
            (declaredSize ushr 21 and 0x7F).toByte(),
            (declaredSize ushr 14 and 0x7F).toByte(),
            (declaredSize ushr 7 and 0x7F).toByte(),
            (declaredSize and 0x7F).toByte(),
        ).copyInto(out)
        body.copyInto(out, HEADER_LENGTH)
        return out
    }

    /** Removes an existing ID3v2 tag by shifting the audio to the file start. */
    fun delete(io: FileIo) {
        val oldTagSize = existingTagSize(io)
        if (oldTagSize == 0L) return
        io.position = oldTagSize
        ShiftData.shiftDataByOffsetToShrinkSpace(io, oldTagSize.toInt())
        io.flush()
    }

    // ---- frame serialization ----

    private fun encodeFrames(tag: Id3v2Tag): ByteArray {
        val version = tag.version
        val out = Buffer()
        for (frame in tag.frames) {
            val body = try {
                encodeFrameBody(version, frame)
            } catch (_: Exception) {
                continue // drop frames we cannot encode rather than corrupt the tag
            }
            if (body.isEmpty()) continue
            writeFrameHeader(out, version, frame.id, body.size)
            out.write(body)
        }
        return out.readByteArray()
    }

    private fun writeFrameHeader(out: Buffer, version: Id3v2Version, id: String, size: Int) {
        out.write(Id3TextEncoding.ISO_8859_1.encode(id))
        when (version) {
            Id3v2Version.V22 -> {
                out.writeByte((size ushr 16).toByte())
                out.writeByte((size ushr 8).toByte())
                out.writeByte(size.toByte())
            }

            Id3v2Version.V23 -> {
                out.write(int32BE(size))
                out.writeByte(0)
                out.writeByte(0)
            }

            Id3v2Version.V24 -> {
                out.writeByte((size ushr 21 and 0x7F).toByte())
                out.writeByte((size ushr 14 and 0x7F).toByte())
                out.writeByte((size ushr 7 and 0x7F).toByte())
                out.writeByte((size and 0x7F).toByte())
                out.writeByte(0)
                out.writeByte(0)
            }
        }
    }

    /** Picks the cheapest encoding this tag version allows for [texts]. */
    private fun chooseEncoding(version: Id3v2Version, vararg texts: String): Id3TextEncoding {
        val fitsLatin = texts.all { text -> text.all { it.code <= 0xFF } }
        return when {
            fitsLatin -> Id3TextEncoding.ISO_8859_1
            version == Id3v2Version.V24 -> Id3TextEncoding.UTF_8
            else -> Id3TextEncoding.UTF_16
        }
    }

    private fun Buffer.writeNulTerminated(encoding: Id3TextEncoding, text: String) {
        write(encoding.encode(text))
        write(ByteArray(encoding.nulWidth))
    }

    private fun encodeFrameBody(version: Id3v2Version, frame: Id3v2Frame): ByteArray {
        val out = Buffer()
        when (frame) {
            is Id3v2Frame.NumberTotal -> {
                val text = frame.part.toRawText()
                val encoding = chooseEncoding(version, text)
                out.writeByte(encoding.id.toByte())
                out.write(encoding.encode(text))
            }

            is Id3v2Frame.Text -> {
                val encoding = chooseEncoding(version, *frame.values.toTypedArray())
                out.writeByte(encoding.id.toByte())
                frame.values.forEachIndexed { index, value ->
                    if (index > 0) out.write(ByteArray(encoding.nulWidth))
                    out.write(encoding.encode(value))
                }
            }

            is Id3v2Frame.UserText -> {
                val encoding = chooseEncoding(version, frame.description, frame.value)
                out.writeByte(encoding.id.toByte())
                out.writeNulTerminated(encoding, frame.description)
                out.write(encoding.encode(frame.value))
            }

            is Id3v2Frame.Url -> out.write(Id3TextEncoding.ISO_8859_1.encode(frame.url))

            is Id3v2Frame.UserUrl -> {
                val encoding = chooseEncoding(version, frame.description)
                out.writeByte(encoding.id.toByte())
                out.writeNulTerminated(encoding, frame.description)
                out.write(Id3TextEncoding.ISO_8859_1.encode(frame.url))
            }

            is Id3v2Frame.LanguageText -> {
                val encoding = chooseEncoding(version, frame.description, frame.text)
                out.writeByte(encoding.id.toByte())
                val language = frame.language.padEnd(3).substring(0, 3)
                out.write(Id3TextEncoding.ISO_8859_1.encode(language))
                out.writeNulTerminated(encoding, frame.description)
                out.write(encoding.encode(frame.text))
            }

            is Id3v2Frame.Picture -> {
                val encoding = chooseEncoding(version, frame.description)
                out.writeByte(encoding.id.toByte())
                if (version == Id3v2Version.V22) {
                    val format = when (frame.mimeType.lowercase()) {
                        "image/jpeg", "image/jpg" -> "JPG"
                        "image/png" -> "PNG"
                        "image/gif" -> "GIF"
                        else -> frame.mimeType.padEnd(3).substring(0, 3).uppercase()
                    }
                    out.write(Id3TextEncoding.ISO_8859_1.encode(format))
                } else {
                    out.writeNulTerminated(Id3TextEncoding.ISO_8859_1, frame.mimeType)
                }
                out.writeByte(frame.pictureType.toByte())
                out.writeNulTerminated(encoding, frame.description)
                out.write(frame.data)
            }

            is Id3v2Frame.UniqueFileId -> {
                out.writeNulTerminated(Id3TextEncoding.ISO_8859_1, frame.owner)
                out.write(frame.data)
            }

            is Id3v2Frame.Popularimeter -> {
                out.writeNulTerminated(Id3TextEncoding.ISO_8859_1, frame.email)
                out.writeByte(frame.rating.toByte())
                out.write(int32BE(frame.counter.toInt()))
            }

            is Id3v2Frame.PairedText -> {
                val texts = frame.pairs.flatMap { listOf(it.first, it.second) }
                val encoding = chooseEncoding(version, *texts.toTypedArray())
                out.writeByte(encoding.id.toByte())
                texts.forEachIndexed { index, value ->
                    if (index > 0) out.write(ByteArray(encoding.nulWidth))
                    out.write(encoding.encode(value))
                }
            }

            is Id3v2Frame.Unknown -> out.write(frame.rawBody)
        }
        return out.readByteArray()
    }
}
