package org.jaudiotagger.kt.audio.asf

import kotlin.time.Duration.Companion.seconds
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.ShiftData
import org.jaudiotagger.kt.io.int32LE
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.readUInt16LE
import org.jaudiotagger.kt.io.u
import org.jaudiotagger.kt.tag.asf.AsfTag

/**
 * ASF (WMA) container. Read/write ports [org.jaudiotagger.audio.asf.util.TagConverter]
 * and [org.jaudiotagger.audio.asf.AsfFileWriter].
 */
internal object AsfFile {

    private val GUID_HEADER = guid(
        0x30, 0x26, 0xb2, 0x75, 0x8e, 0x66, 0xcf, 0x11, 0xa6, 0xd9, 0x00, 0xaa, 0x00, 0x62, 0xce, 0x6c
    )
    private val GUID_FILE_PROPERTIES = guid(
        0xA1, 0xDC, 0xAB, 0x8C, 0x47, 0xA9, 0xCF, 0x11, 0x8E, 0xE4, 0x00, 0xC0, 0x0C, 0x20, 0x53, 0x65
    )
    private val GUID_STREAM_PROPERTIES = guid(
        0x91, 0x07, 0xDC, 0xB7, 0xB7, 0xA9, 0xCF, 0x11, 0x8E, 0xE6, 0x00, 0xC0, 0x0C, 0x20, 0x53, 0x65
    )
    private val GUID_AUDIO_STREAM = guid(
        0x40, 0x9E, 0x69, 0xF8, 0x4D, 0x5B, 0xCF, 0x11, 0xA8, 0xFD, 0x00, 0x80, 0x5F, 0x5C, 0x44, 0x2B
    )
    private val GUID_CONTENT_DESCRIPTION = AsfContainerType.CONTENT_DESCRIPTION.guid
    private val GUID_EXT_CONTENT_DESCRIPTION = AsfContainerType.EXTENDED_CONTENT.guid
    private val GUID_HEADER_EXTENSION = guid(
        0xb5, 0x03, 0xbf, 0x5f, 0x2E, 0xA9, 0xCF, 0x11, 0x8e, 0xe3, 0x00, 0xc0, 0x0c, 0x20, 0x53, 0x65
    )
    private val GUID_METADATA = AsfContainerType.METADATA_OBJECT.guid
    private val GUID_METADATA_LIBRARY = AsfContainerType.METADATA_LIBRARY.guid

    private enum class ChunkPlacement { MAIN_HEADER, HEADER_EXTENSION, ABSENT }

    private data class ContainerPresence(
        val main: Boolean = false,
        val extension: Boolean = false,
    ) {
        fun placement(index: Int): ChunkPlacement = when {
            main -> ChunkPlacement.MAIN_HEADER
            extension -> ChunkPlacement.HEADER_EXTENSION
            index <= 2 -> ChunkPlacement.MAIN_HEADER
            else -> ChunkPlacement.HEADER_EXTENSION
        }
    }

    private fun guid(vararg bytes: Int): ByteArray = ByteArray(16) { bytes[it].toByte() }

    private fun ByteArray.guidAt(offset: Int, guid: ByteArray): Boolean {
        for (i in 0 until 16) {
            if (this[offset + i] != guid[i]) return false
        }
        return true
    }

    private fun ByteArray.readInt64LE(offset: Int): Long =
        readInt32LE(offset).toUInt().toLong() or (readInt32LE(offset + 4).toLong() shl 32)

    private fun utf16le(data: ByteArray, from: Int, to: Int): String {
        val chars = CharArray((to - from) / 2)
        for (i in chars.indices) {
            chars[i] = ((u(data[from + i * 2 + 1]) shl 8) or u(data[from + i * 2])).toChar()
        }
        return chars.concatToString().trimEnd('\u0000')
    }

    private fun utf16leNul(text: String): ByteArray {
        val out = ByteArray(text.length * 2 + 2)
        for (i in text.indices) {
            out[i * 2] = (text[i].code and 0xFF).toByte()
            out[i * 2 + 1] = (text[i].code ushr 8).toByte()
        }
        return out
    }

    private class HeaderObject(val guid: ByteArray, val data: ByteArray)

    private class Header(
        val fileEnd: Long,
        val objects: MutableList<HeaderObject>,
    )

    private fun readHeader(io: FileIo): Header {
        io.position = 0
        if (io.size < 30) throw CannotReadException("Not an ASF file")
        val preamble = io.readFully(30)
        if (!preamble.guidAt(0, GUID_HEADER)) {
            throw CannotReadException("Not an ASF file: missing header GUID")
        }
        val headerSize = preamble.readInt64LE(16)
        if (headerSize < 30 || headerSize > io.size) throw CannotReadException("Corrupt ASF header")

        val objects = mutableListOf<HeaderObject>()
        var position = 30L
        while (position + 24 <= headerSize) {
            io.position = position
            val objHeader = io.readFully(24)
            val size = objHeader.readInt64LE(16)
            if (size < 24 || position + size > headerSize) break
            objects += HeaderObject(
                objHeader.copyOfRange(0, 16),
                io.readFully((size - 24).toInt()),
            )
            position += size
        }
        return Header(headerSize, objects)
    }

    fun readProperties(io: FileIo): AudioProperties {
        val header = readHeader(io)

        var durationSeconds = 0.0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var bitRate = 0
        var encodingType = "ASF"

        for (obj in header.objects) {
            when {
                obj.guid.contentEquals(GUID_FILE_PROPERTIES) -> {
                    if (obj.data.size >= 64) {
                        val playDuration = obj.data.readInt64LE(40)
                        val preroll = obj.data.readInt64LE(56)
                        durationSeconds = playDuration / 1e7 - preroll / 1000.0
                    }
                }

                obj.guid.contentEquals(GUID_STREAM_PROPERTIES) -> {
                    if (obj.data.size >= 54 + 16 && obj.data.guidAt(0, GUID_AUDIO_STREAM)) {
                        val wf = 54
                        val formatTag = obj.data.readUInt16LE(wf)
                        channels = obj.data.readUInt16LE(wf + 2)
                        sampleRate = obj.data.readInt32LE(wf + 4)
                        bitRate = obj.data.readInt32LE(wf + 8) * 8 / 1000
                        bitsPerSample = obj.data.readUInt16LE(wf + 14)
                        encodingType = when (formatTag) {
                            0x161 -> "ASF (audio): 0x0161 (Windows Media Audio (ver 7,8,9))"
                            0x162 -> "ASF (audio): 0x0162 (Windows Media Audio 9 series (Professional))"
                            0x163 -> "ASF (audio): 0x0163 (Windows Media Audio 9 series (Lossless))"
                            0x1 -> "ASF (audio): 0x0001 (PCM)"
                            else -> "ASF (audio): 0x${formatTag.toString(16)}"
                        }
                    }
                }
            }
        }

        if (durationSeconds <= 0 && sampleRate == 0) {
            throw CannotReadException("Corrupt ASF file: no usable stream information")
        }

        return AudioProperties(
            format = "WMA",
            encodingType = encodingType,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            bitRate = bitRate,
            isVariableBitRate = false,
            isLossless = false,
            duration = durationSeconds.seconds,
        )
    }

    // ---- tag reading (TagConverter.createTagOf) ----

    fun readTag(io: FileIo): AsfTag {
        val header = readHeader(io)
        val tag = AsfTag()
        val collected = mutableMapOf<AsfContainerType, MutableList<AsfMetadataDescriptor>>()

        for (obj in header.objects) {
            when {
                obj.guid.contentEquals(GUID_CONTENT_DESCRIPTION) -> parseContentDescription(obj.data, tag)
                obj.guid.contentEquals(GUID_EXT_CONTENT_DESCRIPTION) ->
                    parseDescriptorContainer(obj.data, AsfContainerType.EXTENDED_CONTENT, collected)
                obj.guid.contentEquals(GUID_HEADER_EXTENSION) ->
                    parseHeaderExtensionForRead(obj.data, collected)
            }
        }

        for (type in AsfContainerType.readOrder) {
            collected[type]?.forEach { tag.addDescriptor(it) }
        }
        return tag
    }

    private fun parseContentDescription(data: ByteArray, tag: AsfTag) {
        if (data.size < 10) return
        val lengths = IntArray(5) { data.readUInt16LE(it * 2) }
        var pos = 10
        val values = mutableListOf<String>()
        for (length in lengths) {
            if (pos + length > data.size) return
            values += utf16le(data, pos, pos + length)
            pos += length
        }
        tag.title = values[0]
        tag.author = values[1]
        tag.copyright = values[2]
        tag.description = values[3]
        tag.rating = values[4]
    }

    private fun parseDescriptorContainer(
        data: ByteArray,
        type: AsfContainerType,
        collected: MutableMap<AsfContainerType, MutableList<AsfMetadataDescriptor>>,
    ) {
        if (data.size < 2) return
        val count = data.readUInt16LE(0)
        var pos = 2
        val bucket = collected.getOrPut(type) { mutableListOf() }
        for (i in 0 until count) {
            val parsed = if (type == AsfContainerType.EXTENDED_CONTENT) {
                AsfMetadataDescriptor.fromExtendedContentRecord(data, pos, data.size)
            } else {
                AsfMetadataDescriptor.fromMetadataRecord(data, pos, data.size)
            } ?: continue
            bucket += parsed.first
            pos = parsed.second
        }
    }

    private fun parseHeaderExtensionForRead(
        data: ByteArray,
        collected: MutableMap<AsfContainerType, MutableList<AsfMetadataDescriptor>>,
    ) {
        var pos = 22
        while (pos + 24 <= data.size) {
            val size = data.readInt64LE(pos + 16)
            if (size < 24 || pos + size > data.size) break
            val type = when {
                data.guidAt(pos, GUID_EXT_CONTENT_DESCRIPTION) -> AsfContainerType.EXTENDED_CONTENT
                data.guidAt(pos, GUID_METADATA) -> AsfContainerType.METADATA_OBJECT
                data.guidAt(pos, GUID_METADATA_LIBRARY) -> AsfContainerType.METADATA_LIBRARY
                else -> null
            }
            if (type != null) {
                parseDescriptorContainer(
                    data.copyOfRange(pos + 24, pos + size.toInt()),
                    type,
                    collected,
                )
            }
            pos += size.toInt()
        }
    }

    // ---- tag writing (AsfFileWriter + TagConverter.distributeMetadata) ----

    fun writeTag(io: FileIo, tag: AsfTag) {
        val header = readHeader(io)
        val presence = scanContainerPresence(header)
        val distribution = AsfTagConverter.distributeMetadata(tag)

        header.objects.removeAll { obj ->
            obj.guid.contentEquals(GUID_CONTENT_DESCRIPTION) ||
                obj.guid.contentEquals(GUID_EXT_CONTENT_DESCRIPTION) ||
                obj.guid.contentEquals(GUID_METADATA) ||
                obj.guid.contentEquals(GUID_METADATA_LIBRARY)
        }

        val extIndex = header.objects.indexOfFirst { it.guid.contentEquals(GUID_HEADER_EXTENSION) }
        val oldExtData = if (extIndex >= 0) header.objects[extIndex].data else null
        if (extIndex >= 0) header.objects.removeAt(extIndex)

        val extEmbedded = mutableListOf<HeaderObject>()
        if (oldExtData != null) {
            var pos = 22
            while (pos + 24 <= oldExtData.size) {
                val size = oldExtData.readInt64LE(pos + 16)
                if (size < 24 || pos + size > oldExtData.size) break
                val guid = oldExtData.copyOfRange(pos, pos + 16)
                val objData = oldExtData.copyOfRange(pos + 24, pos + size.toInt())
                if (!guid.contentEquals(GUID_EXT_CONTENT_DESCRIPTION) &&
                    !guid.contentEquals(GUID_METADATA) &&
                    !guid.contentEquals(GUID_METADATA_LIBRARY)
                ) {
                    extEmbedded += HeaderObject(guid, objData)
                }
                pos += size.toInt()
            }
        }

        val cdData = buildContentDescription(tag)
        insertMainObject(header, GUID_CONTENT_DESCRIPTION, cdData, presence.mainFor(0))

        val ecdData = writeMetadataContainer(
            AsfContainerType.EXTENDED_CONTENT,
            distribution.get(AsfContainerType.EXTENDED_CONTENT),
        )
        val ecdDescriptors = distribution.get(AsfContainerType.EXTENDED_CONTENT)
        if (ecdDescriptors.isNotEmpty()) {
            when (presence.placement(2)) {
                ChunkPlacement.MAIN_HEADER ->
                    insertMainObject(header, GUID_EXT_CONTENT_DESCRIPTION, ecdData, presence.mainFor(2))
                ChunkPlacement.HEADER_EXTENSION ->
                    extEmbedded += HeaderObject(GUID_EXT_CONTENT_DESCRIPTION, ecdData)
                ChunkPlacement.ABSENT ->
                    insertMainObject(header, GUID_EXT_CONTENT_DESCRIPTION, ecdData, false)
            }
        }

        val metadataDescriptors = distribution.get(AsfContainerType.METADATA_OBJECT)
        if (metadataDescriptors.isNotEmpty()) {
            extEmbedded += HeaderObject(
                GUID_METADATA,
                writeMetadataContainer(AsfContainerType.METADATA_OBJECT, metadataDescriptors),
            )
        }

        val metadataLibraryDescriptors = distribution.get(AsfContainerType.METADATA_LIBRARY)
        if (metadataLibraryDescriptors.isNotEmpty()) {
            extEmbedded += HeaderObject(
                GUID_METADATA_LIBRARY,
                writeMetadataContainer(AsfContainerType.METADATA_LIBRARY, metadataLibraryDescriptors),
            )
        }

        if (extEmbedded.isNotEmpty() || oldExtData != null) {
            val prefix = oldExtData?.copyOfRange(0, 22) ?: defaultHeaderExtensionPrefix()
            val newExtData = buildHeaderExtension(prefix, extEmbedded)
            insertMainObject(header, GUID_HEADER_EXTENSION, newExtData, extIndex >= 0)
        }

        writeHeader(io, header)
    }

    fun deleteTag(io: FileIo) {
        val header = readHeader(io)
        header.objects.removeAll {
            it.guid.contentEquals(GUID_CONTENT_DESCRIPTION) ||
                it.guid.contentEquals(GUID_EXT_CONTENT_DESCRIPTION)
        }
        val extIndex = header.objects.indexOfFirst { it.guid.contentEquals(GUID_HEADER_EXTENSION) }
        if (extIndex >= 0) {
            val oldExt = header.objects[extIndex].data
            val kept = mutableListOf<HeaderObject>()
            var pos = 22
            while (pos + 24 <= oldExt.size) {
                val size = oldExt.readInt64LE(pos + 16)
                if (size < 24 || pos + size > oldExt.size) break
                val guid = oldExt.copyOfRange(pos, pos + 16)
                if (!guid.contentEquals(GUID_EXT_CONTENT_DESCRIPTION) &&
                    !guid.contentEquals(GUID_METADATA) &&
                    !guid.contentEquals(GUID_METADATA_LIBRARY)
                ) {
                    kept += HeaderObject(guid, oldExt.copyOfRange(pos + 24, pos + size.toInt()))
                }
                pos += size.toInt()
            }
            if (kept.isEmpty()) {
                header.objects.removeAt(extIndex)
            } else {
                header.objects[extIndex] = HeaderObject(
                    GUID_HEADER_EXTENSION,
                    buildHeaderExtension(oldExt.copyOfRange(0, 22), kept),
                )
            }
        }
        writeHeader(io, header)
    }

    private fun scanContainerPresence(header: Header): Map<AsfContainerType, ContainerPresence> {
        val result = mutableMapOf<AsfContainerType, ContainerPresence>()
        for (obj in header.objects) {
            when {
                obj.guid.contentEquals(GUID_CONTENT_DESCRIPTION) ->
                    markMain(result, AsfContainerType.CONTENT_DESCRIPTION)
                obj.guid.contentEquals(GUID_EXT_CONTENT_DESCRIPTION) ->
                    markMain(result, AsfContainerType.EXTENDED_CONTENT)
                obj.guid.contentEquals(GUID_HEADER_EXTENSION) -> {
                    var pos = 22
                    while (pos + 24 <= obj.data.size) {
                        val size = obj.data.readInt64LE(pos + 16)
                        if (size < 24 || pos + size > obj.data.size) break
                        when {
                            obj.data.guidAt(pos, GUID_EXT_CONTENT_DESCRIPTION) ->
                                markExtension(result, AsfContainerType.EXTENDED_CONTENT)
                            obj.data.guidAt(pos, GUID_METADATA) ->
                                markExtension(result, AsfContainerType.METADATA_OBJECT)
                            obj.data.guidAt(pos, GUID_METADATA_LIBRARY) ->
                                markExtension(result, AsfContainerType.METADATA_LIBRARY)
                        }
                        pos += size.toInt()
                    }
                }
            }
        }
        return result
    }

    private fun markMain(
        result: MutableMap<AsfContainerType, ContainerPresence>,
        type: AsfContainerType,
    ) {
        val current = result[type] ?: ContainerPresence()
        result[type] = current.copy(main = true)
    }

    private fun markExtension(
        result: MutableMap<AsfContainerType, ContainerPresence>,
        type: AsfContainerType,
    ) {
        val current = result[type] ?: ContainerPresence()
        result[type] = current.copy(extension = true)
    }

    private fun Map<AsfContainerType, ContainerPresence>.mainFor(index: Int): Boolean {
        val type = AsfContainerType.distributionOrder[index]
        return get(type)?.main == true
    }

    private fun Map<AsfContainerType, ContainerPresence>.extensionFor(index: Int): Boolean {
        val type = AsfContainerType.distributionOrder[index]
        return get(type)?.extension == true
    }

    private fun Map<AsfContainerType, ContainerPresence>.anyFor(index: Int): Boolean {
        val type = AsfContainerType.distributionOrder[index]
        val presence = get(type) ?: return false
        return presence.main || presence.extension
    }

    private fun Map<AsfContainerType, ContainerPresence>.placement(index: Int): ChunkPlacement {
        val type = AsfContainerType.distributionOrder[index]
        val presence = get(type) ?: return if (index <= 2) ChunkPlacement.MAIN_HEADER else ChunkPlacement.HEADER_EXTENSION
        return presence.placement(index)
    }

    private fun insertMainObject(header: Header, guid: ByteArray, data: ByteArray, replaceExisting: Boolean) {
        if (replaceExisting) {
            val index = header.objects.indexOfFirst { it.guid.contentEquals(guid) }
            if (index >= 0) {
                header.objects[index] = HeaderObject(guid, data)
                return
            }
        }
        header.objects += HeaderObject(guid, data)
    }

    private fun defaultHeaderExtensionPrefix(): ByteArray {
        // reserved GUID + reserved uint16(6) + extension data size (patched later)
        val prefix = ByteArray(22)
        prefix[16] = 6
        return prefix
    }

    private fun buildHeaderExtension(prefix: ByteArray, embedded: List<HeaderObject>): ByteArray {
        val body = Buffer()
        for (obj in embedded) {
            body.write(obj.guid)
            val size = 24L + obj.data.size
            body.write(int32LE(size.toInt()))
            body.write(int32LE((size ushr 32).toInt()))
            body.write(obj.data)
        }
        val embeddedBytes = body.readByteArray()
        val outPrefix = prefix.copyOf()
        val extensionSize = embeddedBytes.size
        outPrefix[18] = (extensionSize and 0xFF).toByte()
        outPrefix[19] = ((extensionSize ushr 8) and 0xFF).toByte()
        outPrefix[20] = ((extensionSize ushr 16) and 0xFF).toByte()
        outPrefix[21] = ((extensionSize ushr 24) and 0xFF).toByte()
        return outPrefix + embeddedBytes
    }

    private fun buildContentDescription(tag: AsfTag): ByteArray {
        val values = listOf(tag.title, tag.author, tag.copyright, tag.description, tag.rating)
            .map { utf16leNul(it) }
        val out = Buffer()
        values.forEach { out.write(byteArrayOf(it.size.toByte(), (it.size ushr 8).toByte())) }
        values.forEach { out.write(it) }
        return out.readByteArray()
    }

    private fun patchFilePropertiesSize(header: Header, sizeDelta: Long) {
        if (sizeDelta == 0L) return
        val index = header.objects.indexOfFirst { it.guid.contentEquals(GUID_FILE_PROPERTIES) }
        if (index < 0) return
        val obj = header.objects[index]
        if (obj.data.size < 24) return
        val data = obj.data.copyOf()
        val storedSize = data.readInt64LE(16)
        writeInt64LE(data, 16, storedSize + sizeDelta)
        header.objects[index] = HeaderObject(obj.guid, data)
    }

    private fun writeInt64LE(data: ByteArray, offset: Int, value: Long) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        data[offset + 4] = ((value ushr 32) and 0xFF).toByte()
        data[offset + 5] = ((value ushr 40) and 0xFF).toByte()
        data[offset + 6] = ((value ushr 48) and 0xFF).toByte()
        data[offset + 7] = ((value ushr 56) and 0xFF).toByte()
    }

    private fun writeHeader(io: FileIo, header: Header) {
        val newHeaderSize = 30L + header.objects.sumOf { 24L + it.data.size }
        val delta = (newHeaderSize - header.fileEnd).toInt()
        if (delta != 0) {
            patchFilePropertiesSize(header, delta.toLong())
        }

        val body = Buffer()
        for (obj in header.objects) {
            body.write(obj.guid)
            val size = 24L + obj.data.size
            body.write(int32LE(size.toInt()))
            body.write(int32LE((size ushr 32).toInt()))
            body.write(obj.data)
        }
        val bodyBytes = body.readByteArray()

        if (delta != 0) {
            io.position = header.fileEnd
            if (delta > 0) {
                ShiftData.shiftDataByOffsetToMakeSpace(io, delta)
            } else {
                ShiftData.shiftDataByOffsetToShrinkSpace(io, -delta)
            }
        }

        val out = Buffer()
        out.write(GUID_HEADER)
        out.write(int32LE(newHeaderSize.toInt()))
        out.write(int32LE((newHeaderSize ushr 32).toInt()))
        out.write(int32LE(header.objects.size))
        out.writeByte(0x01)
        out.writeByte(0x02)
        io.position = 0
        io.write(out.readByteArray())
        io.write(bodyBytes)
        io.flush()
    }
}
