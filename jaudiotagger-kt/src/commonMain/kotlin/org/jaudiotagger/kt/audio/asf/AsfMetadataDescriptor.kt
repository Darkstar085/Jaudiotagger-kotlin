package org.jaudiotagger.kt.audio.asf

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.io.int32LE
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.readUInt16LE

/**
 * One ASF metadata descriptor. Ported from
 * [org.jaudiotagger.audio.asf.data.MetadataDescriptor].
 */
internal class AsfMetadataDescriptor(
    val name: String,
    val valueType: Int,
    val languageIndex: Int = 0,
    val streamNumber: Int = 0,
    val content: ByteArray,
) {
    fun sameKey(other: AsfMetadataDescriptor): Boolean =
        name.equals(other.name, ignoreCase = true) &&
            languageIndex == other.languageIndex &&
            streamNumber == other.streamNumber

    fun asStringValue(): String? = when (valueType) {
        TYPE_STRING -> decodeUtf16Le(content, 0, content.size)
        TYPE_BOOLEAN -> if (content.isNotEmpty() && content[0] != 0.toByte()) "true" else "false"
        TYPE_DWORD -> if (content.size >= 4) content.readInt32LE(0).toUInt().toString() else null
        TYPE_QWORD -> if (content.size >= 8) {
            val low = content.readInt32LE(0).toUInt().toLong()
            val high = content.readInt32LE(4).toLong()
            (low or (high shl 32)).toULong().toString()
        } else null
        TYPE_WORD -> if (content.size >= 2) content.readUInt16LE(0).toString() else null
        else -> null
    }

    fun writeRecord(out: Buffer, container: AsfContainerType) {
        val binaryData = if (valueType == TYPE_BOOLEAN) {
            val bytes = ByteArray(if (container == AsfContainerType.EXTENDED_CONTENT) 4 else 2)
            bytes[0] = if (content.isNotEmpty() && content[0] != 0.toByte()) 1 else 0
            bytes
        } else {
            content
        }
        if (container != AsfContainerType.EXTENDED_CONTENT) {
            out.write(byteArrayOf(languageIndex.toByte(), (languageIndex ushr 8).toByte()))
            out.write(byteArrayOf(streamNumber.toByte(), (streamNumber ushr 8).toByte()))
        }
        val nameBytes = utf16leNul(name)
        out.write(byteArrayOf(nameBytes.size.toByte(), (nameBytes.size ushr 8).toByte()))
        if (container == AsfContainerType.EXTENDED_CONTENT) {
            out.write(nameBytes)
        }
        out.write(byteArrayOf(valueType.toByte(), (valueType ushr 8).toByte()))
        var contentLen = binaryData.size
        if (valueType == TYPE_STRING) contentLen += 2
        if (container == AsfContainerType.EXTENDED_CONTENT) {
            out.write(byteArrayOf(contentLen.toByte(), (contentLen ushr 8).toByte()))
        } else {
            out.write(int32LE(contentLen))
        }
        if (container != AsfContainerType.EXTENDED_CONTENT) {
            out.write(nameBytes)
        }
        out.write(binaryData)
        if (valueType == TYPE_STRING) {
            out.writeByte(0)
            out.writeByte(0)
        }
    }

    companion object {
        const val TYPE_STRING = 0
        const val TYPE_BINARY = 1
        const val TYPE_BOOLEAN = 2
        const val TYPE_DWORD = 3
        const val TYPE_QWORD = 4
        const val TYPE_WORD = 5
        const val TYPE_GUID = 6
        const val MAX_LANG_INDEX = 127
        const val MAX_STREAM_NUMBER = 127

        fun text(name: String, value: String): AsfMetadataDescriptor =
            AsfMetadataDescriptor(name, TYPE_STRING, content = utf16leBytes(value))

        fun binary(name: String, data: ByteArray): AsfMetadataDescriptor =
            AsfMetadataDescriptor(name, TYPE_BINARY, content = data)

        fun fromExtendedContentRecord(
            data: ByteArray,
            pos: Int,
            recordEnd: Int,
        ): Pair<AsfMetadataDescriptor, Int>? {
            if (pos + 2 > recordEnd) return null
            val nameLength = data.readUInt16LE(pos)
            var cursor = pos + 2
            if (cursor + nameLength + 4 > recordEnd) return null
            val name = decodeUtf16Le(data, cursor, cursor + nameLength)
            cursor += nameLength
            val valueType = data.readUInt16LE(cursor)
            val valueLength = data.readUInt16LE(cursor + 2)
            cursor += 4
            if (cursor + valueLength > recordEnd) return null
            val content = decodeValueContent(data, cursor, valueLength, valueType, extended = true)
            return AsfMetadataDescriptor(name, valueType, content = content) to cursor + valueLength
        }

        fun fromMetadataRecord(
            data: ByteArray,
            pos: Int,
            recordEnd: Int,
        ): Pair<AsfMetadataDescriptor, Int>? {
            if (pos + 12 > recordEnd) return null
            val languageIndex = data.readUInt16LE(pos)
            val streamNumber = data.readUInt16LE(pos + 2)
            val nameLength = data.readUInt16LE(pos + 4)
            val valueType = data.readUInt16LE(pos + 6)
            val valueLength = data.readInt32LE(pos + 8)
            var cursor = pos + 12
            if (cursor + nameLength + valueLength > recordEnd) return null
            val name = decodeUtf16Le(data, cursor, cursor + nameLength)
            cursor += nameLength
            val content = decodeValueContent(data, cursor, valueLength, valueType, extended = false)
            return AsfMetadataDescriptor(
                name,
                valueType,
                languageIndex,
                streamNumber,
                content,
            ) to cursor + valueLength
        }

        private fun decodeValueContent(
            data: ByteArray,
            pos: Int,
            length: Int,
            valueType: Int,
            extended: Boolean,
        ): ByteArray = when (valueType) {
            TYPE_STRING -> {
                val end = pos + length
                val trimmed = if (end - pos >= 2 && data[end - 2] == 0.toByte() && data[end - 1] == 0.toByte()) {
                    end - 2
                } else {
                    end
                }
                data.copyOfRange(pos, trimmed)
            }
            TYPE_BOOLEAN -> {
                val slice = data.copyOfRange(pos, pos + length)
                byteArrayOf(if (slice.isNotEmpty() && slice[0] != 0.toByte()) 1 else 0)
            }
            else -> data.copyOfRange(pos, pos + length)
        }

        private fun decodeUtf16Le(data: ByteArray, from: Int, to: Int): String {
            val chars = CharArray((to - from) / 2)
            for (i in chars.indices) {
                chars[i] = ((data[from + i * 2 + 1].toInt() and 0xFF shl 8) or
                    (data[from + i * 2].toInt() and 0xFF)).toChar()
            }
            return chars.concatToString().trimEnd('\u0000')
        }

        private fun utf16leBytes(text: String): ByteArray {
            val out = ByteArray(text.length * 2)
            for (i in text.indices) {
                out[i * 2] = (text[i].code and 0xFF).toByte()
                out[i * 2 + 1] = (text[i].code ushr 8).toByte()
            }
            return out
        }

        internal fun utf16leNul(text: String): ByteArray {
            val out = ByteArray(text.length * 2 + 2)
            for (i in text.indices) {
                out[i * 2] = (text[i].code and 0xFF).toByte()
                out[i * 2 + 1] = (text[i].code ushr 8).toByte()
            }
            return out
        }
    }
}

internal fun writeMetadataContainer(
    container: AsfContainerType,
    descriptors: List<AsfMetadataDescriptor>,
): ByteArray {
    val out = Buffer()
    out.write(byteArrayOf(descriptors.size.toByte(), (descriptors.size ushr 8).toByte()))
    for (descriptor in descriptors) {
        descriptor.writeRecord(out, container)
    }
    return out.readByteArray()
}
