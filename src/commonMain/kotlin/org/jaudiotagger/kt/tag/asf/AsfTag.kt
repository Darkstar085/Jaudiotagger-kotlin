package org.jaudiotagger.kt.tag.asf

import org.jaudiotagger.kt.audio.asf.AsfMetadataDescriptor
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.u
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag

/**
 * ASF (WMA) metadata: the five legacy Content Description fields plus descriptors
 * from Extended Content, Metadata and Metadata Library objects.
 */
class AsfTag : Tag {

    // legacy Content Description object
    var title: String = ""
    var author: String = ""
    var copyright: String = ""
    var description: String = ""
    var rating: String = ""

    internal val internalDescriptors = mutableListOf<AsfMetadataDescriptor>()

    internal fun addDescriptor(descriptor: AsfMetadataDescriptor) {
        internalDescriptors += descriptor
    }

    private fun legacyGet(name: String): String? = when (name.uppercase()) {
        "TITLE" -> title
        "AUTHOR" -> author
        "COPYRIGHT" -> copyright
        "DESCRIPTION" -> description
        "RATING" -> rating
        else -> null
    }?.ifEmpty { null }

    private fun legacySet(name: String, value: String): Boolean {
        when (name.uppercase()) {
            "TITLE" -> title = value
            "AUTHOR" -> author = value
            "COPYRIGHT" -> copyright = value
            "DESCRIPTION" -> description = value
            "RATING" -> rating = value
            else -> return false
        }
        return true
    }

    private fun isLegacy(name: String): Boolean =
        name.uppercase() in setOf("TITLE", "AUTHOR", "COPYRIGHT", "DESCRIPTION", "RATING")

    private fun nameFor(key: FieldKey): String =
        asfFieldNames[key]
            ?: throw IllegalArgumentException("FieldKey $key is not supported by ASF")

    fun firstRaw(name: String): String? {
        legacyGet(name)?.let { return it }
        return internalDescriptors.firstNotNullOfOrNull { descriptor ->
            if (descriptor.name.equals(name, ignoreCase = true)) descriptor.asStringValue()
                ?.ifEmpty { null } else null
        }
    }

    fun allRaw(name: String): List<String> {
        if (isLegacy(name)) return listOfNotNull(legacyGet(name))
        return internalDescriptors.mapNotNull { descriptor ->
            if (descriptor.name.equals(
                    name,
                    ignoreCase = true
                )
            ) descriptor.asStringValue() else null
        }
    }

    fun setRaw(name: String, value: String) {
        if (legacySet(name, value)) return
        removeRaw(name)
        internalDescriptors += AsfMetadataDescriptor.text(name, value)
    }

    fun addRaw(name: String, value: String) {
        if (legacySet(name, value)) return
        internalDescriptors += AsfMetadataDescriptor.text(name, value)
    }

    fun removeRaw(name: String) {
        if (legacySet(name, "")) return
        internalDescriptors.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    override fun first(key: FieldKey): String? = firstRaw(nameFor(key))

    override fun all(key: FieldKey): List<String> = allRaw(nameFor(key))

    override fun set(key: FieldKey, value: String) = setRaw(nameFor(key), value)

    override fun add(key: FieldKey, value: String) = addRaw(nameFor(key), value)

    override fun remove(key: FieldKey) = removeRaw(nameFor(key))

    override val fieldCount: Int
        get() = internalDescriptors.size +
                listOf(title, author, copyright, description, rating).count { it.isNotEmpty() }

    override val isEmpty: Boolean get() = fieldCount == 0

    override fun clear() {
        title = ""; author = ""; copyright = ""; description = ""; rating = ""
        internalDescriptors.clear()
    }

    override val artworks: List<Artwork>
        get() = internalDescriptors
            .filter { it.name == PICTURE_DESCRIPTOR && it.valueType == AsfMetadataDescriptor.TYPE_BINARY }
            .mapNotNull { decodePicture(it.content) }

    override fun addArtwork(artwork: Artwork) {
        internalDescriptors += AsfMetadataDescriptor.binary(
            PICTURE_DESCRIPTOR,
            encodePicture(artwork)
        )
    }

    override fun clearArtworks() {
        internalDescriptors.removeAll {
            it.name == PICTURE_DESCRIPTOR && it.valueType == AsfMetadataDescriptor.TYPE_BINARY
        }
    }

    override fun toString(): String =
        "AsfTag(title=$title, author=$author, descriptors=$internalDescriptors)"

    companion object {
        const val PICTURE_DESCRIPTOR = "WM/Picture"

        internal fun decodePicture(data: ByteArray): Artwork? {
            if (data.size < 5) return null
            val pictureType = u(data[0])
            val imageSize = data.readInt32LE(1)
            var pos = 5

            fun readUtf16Nul(): String? {
                val start = pos
                while (pos + 2 <= data.size) {
                    if (data[pos].toInt() == 0 && data[pos + 1].toInt() == 0) {
                        val chars = CharArray((pos - start) / 2)
                        for (i in chars.indices) {
                            chars[i] =
                                ((u(data[start + i * 2 + 1]) shl 8) or u(data[start + i * 2])).toChar()
                        }
                        pos += 2
                        return chars.concatToString()
                    }
                    pos += 2
                }
                return null
            }

            val mimeType = readUtf16Nul() ?: return null
            val description = readUtf16Nul() ?: return null
            if (imageSize < 0 || imageSize > data.size - pos) return null
            return Artwork(
                data = data.copyOfRange(pos, pos + imageSize),
                mimeType = mimeType,
                description = description,
                pictureType = pictureType,
            )
        }

        internal fun encodePicture(artwork: Artwork): ByteArray {
            fun utf16Nul(text: String): ByteArray {
                val out = ByteArray(text.length * 2 + 2)
                for (i in text.indices) {
                    out[i * 2] = (text[i].code and 0xFF).toByte()
                    out[i * 2 + 1] = (text[i].code ushr 8).toByte()
                }
                return out
            }

            val mime = utf16Nul(artwork.mimeType)
            val desc = utf16Nul(artwork.description)
            val out = ByteArray(5 + mime.size + desc.size + artwork.data.size)
            out[0] = artwork.pictureType.toByte()
            out[1] = artwork.data.size.toByte()
            out[2] = (artwork.data.size ushr 8).toByte()
            out[3] = (artwork.data.size ushr 16).toByte()
            out[4] = (artwork.data.size ushr 24).toByte()
            mime.copyInto(out, 5)
            desc.copyInto(out, 5 + mime.size)
            artwork.data.copyInto(out, 5 + mime.size + desc.size)
            return out
        }
    }
}
