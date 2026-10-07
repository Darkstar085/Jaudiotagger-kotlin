package org.jaudiotagger.kt.io

/** Unsigned value of a byte as Int. */
internal fun u(b: Byte): Int = b.toInt() and 0xFF

internal fun ByteArray.readUInt16BE(offset: Int): Int =
    (u(this[offset]) shl 8) or u(this[offset + 1])

internal fun ByteArray.readUInt16LE(offset: Int): Int =
    u(this[offset]) or (u(this[offset + 1]) shl 8)

internal fun ByteArray.readUInt24BE(offset: Int): Int =
    (u(this[offset]) shl 16) or (u(this[offset + 1]) shl 8) or u(this[offset + 2])

internal fun ByteArray.readInt32BE(offset: Int): Int =
    (u(this[offset]) shl 24) or (u(this[offset + 1]) shl 16) or
            (u(this[offset + 2]) shl 8) or u(this[offset + 3])

internal fun ByteArray.readInt32LE(offset: Int): Int =
    u(this[offset]) or (u(this[offset + 1]) shl 8) or
            (u(this[offset + 2]) shl 16) or (u(this[offset + 3]) shl 24)

internal fun int32BE(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)

internal fun int32LE(value: Int): ByteArray = byteArrayOf(
    value.toByte(),
    (value ushr 8).toByte(),
    (value ushr 16).toByte(),
    (value ushr 24).toByte(),
)

/** ISO-8859-1 decoding: one byte, one char. Needed for 4CC atom/chunk ids. */
internal fun ByteArray.decodeLatin1(from: Int = 0, to: Int = size): String {
    val chars = CharArray(to - from)
    for (i in from until to) {
        chars[i - from] = u(this[i]).toChar()
    }
    return chars.concatToString()
}

private val hexDigits = "0123456789abcdef".toCharArray()

internal fun ByteArray.toHex(offset: Int = 0, length: Int = size - offset): String {
    val chars = CharArray(length * 2)
    for (i in 0 until length) {
        val v = u(this[offset + i])
        chars[i * 2] = hexDigits[v ushr 4]
        chars[i * 2 + 1] = hexDigits[v and 0x0F]
    }
    return chars.concatToString()
}
