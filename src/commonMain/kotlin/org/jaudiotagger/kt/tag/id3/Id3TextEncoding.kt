package org.jaudiotagger.kt.tag.id3

import org.jaudiotagger.kt.io.u

/**
 * The four text encodings ID3v2 allows, identified by the leading byte of
 * text-bearing frames. UTF-16 variants are decoded manually because common
 * Kotlin has no charset API.
 */
internal enum class Id3TextEncoding(val id: Int) {
    ISO_8859_1(0),
    UTF_16(1), // with BOM
    UTF_16BE(2), // v2.4 only
    UTF_8(3); // v2.4 only

    /** Terminator width in bytes for NUL-terminated strings of this encoding. */
    val nulWidth: Int get() = if (this == UTF_16 || this == UTF_16BE) 2 else 1

    fun decode(data: ByteArray, from: Int = 0, to: Int = data.size): String = when (this) {
        ISO_8859_1 -> buildString(to - from) {
            for (i in from until to) append(u(data[i]).toChar())
        }

        UTF_8 -> data.decodeToString(from, to)

        UTF_16 -> {
            when {
                to - from >= 2 && u(data[from]) == 0xFF && u(data[from + 1]) == 0xFE ->
                    decodeUtf16(data, from + 2, to, bigEndian = false)

                to - from >= 2 && u(data[from]) == 0xFE && u(data[from + 1]) == 0xFF ->
                    decodeUtf16(data, from + 2, to, bigEndian = true)

                // no BOM: the spec demands one, but default to LE like most readers
                else -> decodeUtf16(data, from, to, bigEndian = false)
            }
        }

        UTF_16BE -> decodeUtf16(data, from, to, bigEndian = true)
    }

    fun encode(text: String): ByteArray = when (this) {
        ISO_8859_1 -> ByteArray(text.length) { i ->
            val c = text[i].code
            if (c <= 0xFF) c.toByte() else '?'.code.toByte()
        }

        UTF_8 -> text.encodeToByteArray()

        UTF_16 -> ByteArray(2 + text.length * 2).also { out ->
            out[0] = 0xFF.toByte() // LE BOM, matching jaudiotagger's default
            out[1] = 0xFE.toByte()
            for (i in text.indices) {
                out[2 + i * 2] = (text[i].code and 0xFF).toByte()
                out[3 + i * 2] = (text[i].code ushr 8).toByte()
            }
        }

        UTF_16BE -> ByteArray(text.length * 2).also { out ->
            for (i in text.indices) {
                out[i * 2] = (text[i].code ushr 8).toByte()
                out[i * 2 + 1] = (text[i].code and 0xFF).toByte()
            }
        }
    }

    private fun decodeUtf16(data: ByteArray, from: Int, to: Int, bigEndian: Boolean): String {
        val length = (to - from) / 2
        val chars = CharArray(length)
        for (i in 0 until length) {
            val hi = u(data[from + i * 2 + if (bigEndian) 0 else 1])
            val lo = u(data[from + i * 2 + if (bigEndian) 1 else 0])
            chars[i] = ((hi shl 8) or lo).toChar()
        }
        return chars.concatToString()
    }

    companion object {
        fun fromId(id: Int): Id3TextEncoding =
            entries.firstOrNull { it.id == id } ?: ISO_8859_1
    }
}
