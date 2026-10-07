package org.jaudiotagger.kt.tag.vorbiscomment

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.int32LE
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.u

/**
 * Binary codec for the Vorbis Comment packet, shared by FLAC and Ogg:
 *
 * ```
 * [vendor_length]            uint32 LE
 * [vendor_string]            UTF-8
 * [user_comment_list_length] uint32 LE
 * repeat: [length] uint32 LE, [comment] UTF-8 "NAME=value"
 * [framing_bit]              only inside Ogg Vorbis
 * ```
 */
object VorbisCommentCodec {

    /**
     * Comments larger than this are skipped to avoid pathological allocations
     * on corrupt files (matches jaudiotagger behaviour).
     */
    const val MAX_COMMENT_LENGTH = 10_000_000

    fun decode(rawData: ByteArray, hasFramingBit: Boolean): VorbisCommentTag {
        val tag = VorbisCommentTag(vendor = "")
        var pos = 0

        val vendorLength = rawData.readInt32LE(pos)
        pos += 4
        tag.vendor = rawData.decodeToString(pos, pos + vendorLength)
        pos += vendorLength

        val userComments = rawData.readInt32LE(pos)
        pos += 4

        for (i in 0 until userComments) {
            val commentLength = rawData.readInt32LE(pos)
            pos += 4
            if (commentLength > MAX_COMMENT_LENGTH || commentLength > rawData.size - pos) {
                // corrupt length: ignore the rest rather than fail the whole tag
                break
            }
            val comment = rawData.decodeToString(pos, pos + commentLength)
            pos += commentLength
            tag.addParsedField(VorbisCommentField.parse(comment))
        }

        if (hasFramingBit) {
            if (pos >= rawData.size || (u(rawData[pos]) and 0x01) != 1) {
                throw CannotReadException("The OGG framing bit is not set, invalid vorbis comment header")
            }
        }
        return tag
    }

    /**
     * Offset one past the last user comment in [rawData], or null when a comment length is
     * corrupt. Ogg Opus allows padding or binary data after this offset.
     */
    internal fun commentListEnd(rawData: ByteArray): Int? {
        var pos = 0
        val vendorLength = rawData.readInt32LE(pos)
        pos += 4 + vendorLength

        val userComments = rawData.readInt32LE(pos)
        pos += 4

        for (i in 0 until userComments) {
            val commentLength = rawData.readInt32LE(pos)
            pos += 4
            if (commentLength > MAX_COMMENT_LENGTH || commentLength > rawData.size - pos) {
                return null
            }
            pos += commentLength
        }
        return pos
    }

    /** Serializes [tag]; the framing bit (Ogg only) is appended by the Ogg writer. */
    fun encode(tag: VorbisCommentTag): ByteArray {
        val buffer = Buffer()
        val vendorBytes = tag.vendor.encodeToByteArray()
        buffer.write(int32LE(vendorBytes.size))
        buffer.write(vendorBytes)
        buffer.write(int32LE(tag.allFields.size))
        for (field in tag.allFields) {
            val idBytes = field.id.encodeToByteArray()
            val valueBytes = field.value.encodeToByteArray()
            buffer.write(int32LE(idBytes.size + 1 + valueBytes.size))
            buffer.write(idBytes)
            buffer.writeByte('='.code.toByte())
            buffer.write(valueBytes)
        }
        return buffer.readByteArray()
    }
}
