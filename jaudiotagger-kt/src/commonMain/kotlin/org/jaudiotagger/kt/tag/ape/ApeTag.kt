package org.jaudiotagger.kt.tag.ape

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag

/**
 * A single APEv2 item: text (UTF-8) or binary. Item keys are matched
 * case-insensitively; well-known keys are canonicalised via [ApeFieldKey].
 */
sealed class ApeItem(val id: String) {
    class Text(id: String, val value: String) : ApeItem(id) {
        override fun toString(): String = "$id=$value"
    }

    class Binary(id: String, val data: ByteArray) : ApeItem(id) {
        override fun toString(): String = "$id=<${data.size} bytes>"
    }
}

/**
 * APEv2 tag, used by Monkey's Audio and WavPack (stored after the audio data,
 * before any trailing Lyrics3/ID3v1 blocks).
 *
 * Cover art is carried in the "Cover Art (Front)"/"Cover Art (Back)" binary
 * items in the `descriptor\0imagedata` layout.
 */
class ApeTag : Tag {

    private val items = mutableListOf<ApeItem>()

    val allItems: List<ApeItem> get() = items

    private fun canonicalId(id: String): String =
        ApeFieldKey.fromFieldName(id)?.fieldName ?: id

    // ---- string-keyed access (usable for custom/unmapped item names) ----

    fun firstRaw(id: String): String? =
        items.filterIsInstance<ApeItem.Text>().firstOrNull { it.id.equals(id, ignoreCase = true) }?.value

    fun allRaw(id: String): List<String> =
        items.filterIsInstance<ApeItem.Text>().filter { it.id.equals(id, ignoreCase = true) }.map { it.value }

    fun addRaw(id: String, value: String) {
        items += ApeItem.Text(canonicalId(id), value)
    }

    fun setRaw(id: String, value: String) {
        val insertAt = items.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        items.removeAll { it.id.equals(id, ignoreCase = true) }
        if (insertAt >= 0) {
            items.add(insertAt, ApeItem.Text(canonicalId(id), value))
        } else {
            items += ApeItem.Text(canonicalId(id), value)
        }
    }

    fun removeRaw(id: String) {
        items.removeAll { it.id.equals(id, ignoreCase = true) }
    }

    internal fun addItem(item: ApeItem) {
        items += item
    }

    // ---- FieldKey access ----

    private fun apeKey(key: FieldKey): ApeFieldKey =
        fieldKeyToApeKey[key]
            ?: throw IllegalArgumentException("FieldKey $key is not supported by APEv2 tags")

    override fun first(key: FieldKey): String? = firstRaw(apeKey(key).fieldName)

    override fun all(key: FieldKey): List<String> = allRaw(apeKey(key).fieldName)

    override fun set(key: FieldKey, value: String) = setRaw(apeKey(key).fieldName, value)

    override fun add(key: FieldKey, value: String) = addRaw(apeKey(key).fieldName, value)

    override fun remove(key: FieldKey) = removeRaw(apeKey(key).fieldName)

    override val fieldCount: Int get() = items.size

    override val isEmpty: Boolean get() = items.isEmpty()

    override fun clear() {
        items.clear()
    }

    // ---- artwork ----

    override val artworks: List<Artwork>
        get() = items.filterIsInstance<ApeItem.Binary>()
            .filter { isCoverArtId(it.id) }
            .map { decodeCover(it) }

    override fun addArtwork(artwork: Artwork) {
        val descriptor = artwork.mimeType.ifEmpty { "application/octet-stream" }.encodeToByteArray()
        val value = ByteArray(descriptor.size + 1 + artwork.data.size)
        descriptor.copyInto(value)
        artwork.data.copyInto(value, descriptor.size + 1)
        items += ApeItem.Binary(ApeFieldKey.COVER_ART_FRONT.fieldName, value)
    }

    override fun clearArtworks() {
        items.removeAll { it is ApeItem.Binary && isCoverArtId(it.id) }
    }

    private fun isCoverArtId(id: String): Boolean =
        id.equals(ApeFieldKey.COVER_ART_FRONT.fieldName, ignoreCase = true) ||
            id.equals(ApeFieldKey.COVER_ART_BACK.fieldName, ignoreCase = true)

    /** Cover item layout: descriptor (mime type or filename), NUL, image bytes. */
    private fun decodeCover(item: ApeItem.Binary): Artwork {
        val separator = item.data.indexOfFirst { it.toInt() == 0 }
        return if (separator >= 0) {
            Artwork(
                data = item.data.copyOfRange(separator + 1, item.data.size),
                mimeType = item.data.decodeToString(0, separator),
                description = item.id,
            )
        } else {
            Artwork(data = item.data, mimeType = "application/octet-stream", description = item.id)
        }
    }

    override fun toString(): String = "ApeTag($items)"
}
