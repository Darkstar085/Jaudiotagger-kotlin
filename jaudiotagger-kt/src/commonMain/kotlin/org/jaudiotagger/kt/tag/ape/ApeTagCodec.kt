package org.jaudiotagger.kt.tag.ape

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.io.int32LE
import org.jaudiotagger.kt.io.readInt32LE

/**
 * APEv2 footer/header: "APETAGEX", version, tag size (items + footer, without
 * header), item count, flags, 8 reserved bytes — 32 bytes, little-endian.
 */
class ApeTagFooter(
    val version: Int,
    val size: Int,
    val itemCount: Int,
    val flags: Int,
) {
    val isValid: Boolean get() = version >= 2000 && size >= FOOTER_SIZE && itemCount >= 0

    val hasHeader: Boolean get() = (flags and FLAG_HAS_HEADER) != 0
    val isHeader: Boolean get() = (flags and FLAG_IS_HEADER) != 0
    val hasFooter: Boolean get() = (flags and FLAG_HAS_NO_FOOTER) == 0

    /** Offset of the first item given the offset of this footer. */
    fun calculateTagStart(footerOffset: Long): Long = footerOffset - (size - FOOTER_SIZE)

    companion object {
        const val FOOTER_SIZE = 32
        const val CURRENT_VERSION = 2000

        private const val FLAG_HAS_HEADER = 1 shl 31
        private const val FLAG_HAS_NO_FOOTER = 1 shl 30
        private const val FLAG_IS_HEADER = 1 shl 29

        private val IDENTIFIER = "APETAGEX".encodeToByteArray()

        fun parse(buffer: ByteArray, offset: Int = 0): ApeTagFooter? {
            if (buffer.size - offset < FOOTER_SIZE) return null
            for (i in IDENTIFIER.indices) {
                if (buffer[offset + i] != IDENTIFIER[i]) return null
            }
            return ApeTagFooter(
                version = buffer.readInt32LE(offset + 8),
                size = buffer.readInt32LE(offset + 12),
                itemCount = buffer.readInt32LE(offset + 16),
                flags = buffer.readInt32LE(offset + 20),
            )
        }

        fun buildHeader(totalSize: Int, itemCount: Int): ByteArray =
            build(totalSize, itemCount, FLAG_HAS_HEADER or FLAG_IS_HEADER)

        fun buildFooter(totalSize: Int, itemCount: Int): ByteArray =
            build(totalSize, itemCount, FLAG_HAS_HEADER)

        private fun build(totalSize: Int, itemCount: Int, flags: Int): ByteArray {
            val buffer = Buffer()
            buffer.write(IDENTIFIER)
            buffer.write(int32LE(CURRENT_VERSION))
            buffer.write(int32LE(totalSize))
            buffer.write(int32LE(itemCount))
            buffer.write(int32LE(flags))
            buffer.write(ByteArray(8))
            return buffer.readByteArray()
        }
    }
}

/**
 * Binary codec for the APEv2 item list.
 *
 * Item layout: value length uint32 LE, flags uint32 LE, NUL-terminated key
 * (ASCII), value bytes. Flag bit 1 marks a binary value.
 */
object ApeTagCodec {

    private const val FLAG_TYPE_BINARY = 0x1

    /** Parses items from the tag data area (between header and footer). */
    fun decodeItems(data: ByteArray): ApeTag {
        val tag = ApeTag()
        var pos = 0
        while (data.size - pos >= 8) {
            val valueLength = data.readInt32LE(pos)
            val flags = data.readInt32LE(pos + 4)
            pos += 8

            val keyEnd = data.indexOfNul(pos) ?: break
            val id = data.decodeToString(pos, keyEnd)
            pos = keyEnd + 1

            if (valueLength < 0 || valueLength > data.size - pos) break
            val value = data.copyOfRange(pos, pos + valueLength)
            pos += valueLength

            val isBinary = (flags and FLAG_TYPE_BINARY) != 0
            tag.addItem(
                if (isBinary) ApeItem.Binary(id, value)
                else ApeItem.Text(id, value.decodeToString())
            )
        }
        return tag
    }

    /**
     * Serializes the whole tag: header, items, footer.
     * Returns an empty array when the tag has no items.
     */
    fun encodeTag(tag: ApeTag): ByteArray {
        if (tag.isEmpty) return ByteArray(0)

        val body = Buffer()
        for (item in tag.allItems) {
            val value = when (item) {
                is ApeItem.Text -> item.value.encodeToByteArray()
                is ApeItem.Binary -> item.data
            }
            body.write(int32LE(value.size))
            body.write(int32LE(if (item is ApeItem.Binary) FLAG_TYPE_BINARY else 0))
            body.write(item.id.encodeToByteArray())
            body.writeByte(0)
            body.write(value)
        }

        val bodyBytes = body.readByteArray()
        val totalSize = bodyBytes.size + ApeTagFooter.FOOTER_SIZE
        val out = Buffer()
        out.write(ApeTagFooter.buildHeader(totalSize, tag.fieldCount))
        out.write(bodyBytes)
        out.write(ApeTagFooter.buildFooter(totalSize, tag.fieldCount))
        return out.readByteArray()
    }

    private fun ByteArray.indexOfNul(from: Int): Int? {
        for (i in from until size) {
            if (this[i].toInt() == 0) return i
        }
        return null
    }
}
