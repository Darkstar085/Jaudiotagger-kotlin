package org.jaudiotagger.kt.audio.ogg

/**
 * CRC-32 used by Ogg pages (polynomial 0x04c11db7, no bit reversal, zero initial value).
 * The checksum is computed over the whole page with its CRC field zeroed.
 */
internal object OggCrc {

    private val lookup = LongArray(256).also { table ->
        for (i in 0 until 256) {
            var r = i.toLong() shl 24
            repeat(8) {
                r = if ((r and 0x80000000L) != 0L) {
                    (r shl 1) xor 0x04c11db7L
                } else {
                    r shl 1
                }
            }
            table[i] = r and 0xffffffffL
        }
    }

    /** Returns the checksum as 4 little-endian bytes ready to be written to the page. */
    fun computeCrc(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): ByteArray {
        var crc = 0L
        for (i in offset until offset + length) {
            val index = (((crc ushr 24) and 0xff) xor (data[i].toLong() and 0xff)).toInt()
            crc = ((crc shl 8) xor lookup[index]) and 0xffffffffL
        }
        return byteArrayOf(
            (crc and 0xff).toByte(),
            ((crc ushr 8) and 0xff).toByte(),
            ((crc ushr 16) and 0xff).toByte(),
            ((crc ushr 24) and 0xff).toByte(),
        )
    }

    /** Zeroes the CRC field and stamps a freshly computed checksum over [page]. */
    fun stampCrc(page: ByteArray) {
        for (i in 0 until 4) {
            page[OggPageHeader.FIELD_PAGE_CHECKSUM_POS + i] = 0
        }
        val crc = computeCrc(page)
        crc.copyInto(page, OggPageHeader.FIELD_PAGE_CHECKSUM_POS)
    }
}
