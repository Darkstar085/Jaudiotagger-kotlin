package org.jaudiotagger.kt.tag.mp4

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag
import org.jaudiotagger.kt.tag.id3.GenreTypes

/**
 * A single ilst metadata item.
 */
sealed class Mp4Item(val atomId: String) {
    /** UTF-8 text data atom. */
    class Text(atomId: String, var value: String) : Mp4Item(atomId) {
        override fun toString(): String = "$atomId=$value"
    }

    /** Reverse-DNS "----" atom: issuer + identifier + UTF-8 value. */
    class ReverseDns(val issuer: String, val identifier: String, var value: String) :
        Mp4Item("----") {
        override fun toString(): String = "----[$issuer:$identifier]=$value"
    }

    /** trkn/disk: number plus optional total, stored as 16-bit integers. */
    class NumberPair(atomId: String, var number: Int, var total: Int) : Mp4Item(atomId) {
        override fun toString(): String = "$atomId=$number/$total"
    }

    /** covr images; [isPng] selects the data atom type on write. */
    class Cover(val data: ByteArray, val isPng: Boolean) : Mp4Item("covr") {
        override fun toString(): String = "covr(<${data.size} bytes>)"
    }

    /** gnre atom: uint16 genre id (ID3v1 index + 1). */
    class Genre(val genreId: Int) : Mp4Item("gnre") {
        override fun toString(): String = "gnre=$genreId"
    }

    /** Anything the library does not interpret; raw item content is preserved. */
    class Binary(atomId: String, val rawData: ByteArray) : Mp4Item(atomId) {
        override fun toString(): String = "$atomId(<${rawData.size} bytes>)"
    }
}

/**
 * MP4 iTunes-style metadata (the moov/udta/meta/ilst box).
 */
class Mp4Tag : Tag {

    val items = mutableListOf<Mp4Item>()

    private companion object {
        const val GENRE_CUSTOM_ATOM = "\u00A9gen"
    }

    private fun key(fieldKey: FieldKey): Mp4FrameKey =
        mp4FrameKeys[fieldKey]
            ?: throw IllegalArgumentException("FieldKey $fieldKey is not supported by MP4")

    private fun matches(item: Mp4Item, k: Mp4FrameKey): Boolean = when (item) {
        is Mp4Item.ReverseDns ->
            k.issuer != null && item.issuer.equals(k.issuer, ignoreCase = true) &&
                    item.identifier.equals(k.identifier, ignoreCase = true)

        else -> k.issuer == null && item.atomId == k.atomId
    }

    override fun first(key: FieldKey): String? = all(key).firstOrNull()

    override fun all(key: FieldKey): List<String> {
        when (key) {
            FieldKey.GENRE -> return genreValues()

            FieldKey.TRACK -> return numberPair("trkn")?.number?.takeIf { it > 0 }
                ?.let { listOf(it.toString()) } ?: emptyList()

            FieldKey.TRACK_TOTAL -> return numberPair("trkn")?.total?.takeIf { it > 0 }
                ?.let { listOf(it.toString()) } ?: emptyList()

            FieldKey.DISC_NO -> return numberPair("disk")?.number?.takeIf { it > 0 }
                ?.let { listOf(it.toString()) } ?: emptyList()

            FieldKey.DISC_TOTAL -> return numberPair("disk")?.total?.takeIf { it > 0 }
                ?.let { listOf(it.toString()) } ?: emptyList()

            else -> {}
        }
        val k = key(key)
        return items.mapNotNull { item ->
            if (!matches(item, k)) return@mapNotNull null
            when (item) {
                is Mp4Item.Text -> item.value
                is Mp4Item.ReverseDns -> item.value
                is Mp4Item.NumberPair -> item.number.toString()
                else -> null
            }
        }
    }

    override fun set(key: FieldKey, value: String) {
        when (key) {
            FieldKey.GENRE -> return setGenre(value)

            FieldKey.TRACK -> return setNumberPair("trkn", number = value.toIntOrNull() ?: 0)
            FieldKey.TRACK_TOTAL -> return setNumberPair("trkn", total = value.toIntOrNull() ?: 0)
            FieldKey.DISC_NO -> return setNumberPair("disk", number = value.toIntOrNull() ?: 0)
            FieldKey.DISC_TOTAL -> return setNumberPair("disk", total = value.toIntOrNull() ?: 0)
            else -> {}
        }
        val k = key(key)
        items.removeAll { matches(it, k) }
        addItemFor(k, value)
    }

    override fun add(key: FieldKey, value: String) {
        when (key) {
            FieldKey.TRACK, FieldKey.TRACK_TOTAL, FieldKey.DISC_NO, FieldKey.DISC_TOTAL ->
                return set(key, value)

            FieldKey.GENRE -> {
                items += Mp4Item.Text(GENRE_CUSTOM_ATOM, value)
                return
            }

            else -> {}
        }
        addItemFor(key(key), value)
    }

    private fun addItemFor(k: Mp4FrameKey, value: String) {
        items += if (k.issuer != null) {
            Mp4Item.ReverseDns(k.issuer, k.identifier!!, value)
        } else {
            Mp4Item.Text(k.atomId, value)
        }
    }

    override fun remove(key: FieldKey) {
        when (key) {
            FieldKey.GENRE -> {
                items.removeAll { it is Mp4Item.Genre || (it is Mp4Item.Text && it.atomId == GENRE_CUSTOM_ATOM) }
                return
            }

            FieldKey.TRACK -> return deleteNumberPair("trkn", deleteNumber = true)
            FieldKey.TRACK_TOTAL -> return deleteNumberPair("trkn", deleteNumber = false)
            FieldKey.DISC_NO -> return deleteNumberPair("disk", deleteNumber = true)
            FieldKey.DISC_TOTAL -> return deleteNumberPair("disk", deleteNumber = false)

            else -> {}
        }
        val k = key(key)
        items.removeAll { matches(it, k) }
    }

    private fun genreValues(): List<String> {
        val values = mutableListOf<String>()
        for (item in items) {
            when (item) {
                is Mp4Item.Genre -> GenreTypes.nameOf(item.genreId - 1)?.let { values += it }
                is Mp4Item.Text -> if (item.atomId == GENRE_CUSTOM_ATOM) values += item.value
                else -> {}
            }
        }
        return values
    }

    private fun setGenre(value: String) {
        items.removeAll { it is Mp4Item.Genre || (it is Mp4Item.Text && it.atomId == GENRE_CUSTOM_ATOM) }
        value.toShortOrNull()?.let { genreVal ->
            if (genreVal <= GenreTypes.MAX_STANDARD_GENRE_ID) {
                items += Mp4Item.Genre(genreVal + 1)
                return
            }
        }
        GenreTypes.idOf(value)?.let { id ->
            if (id <= GenreTypes.MAX_STANDARD_GENRE_ID) {
                items += Mp4Item.Genre(id + 1)
                return
            }
        }
        items += Mp4Item.Text(GENRE_CUSTOM_ATOM, value)
    }

    private fun numberPair(atomId: String): Mp4Item.NumberPair? =
        items.filterIsInstance<Mp4Item.NumberPair>().firstOrNull { it.atomId == atomId }

    private fun setNumberPair(atomId: String, number: Int? = null, total: Int? = null) {
        val existing = numberPair(atomId)
        if (existing != null) {
            number?.let { existing.number = it }
            total?.let { existing.total = it }
        } else {
            items += Mp4Item.NumberPair(atomId, number ?: 0, total ?: 0)
        }
    }

    private fun deleteNumberPair(atomId: String, deleteNumber: Boolean) {
        val existing = numberPair(atomId) ?: return
        if (deleteNumber) {
            if (existing.total <= 0) {
                items.removeAll { it === existing }
            } else {
                existing.number = 0
            }
        } else {
            if (existing.number <= 0) {
                items.removeAll { it === existing }
            } else {
                existing.total = 0
            }
        }
    }

    override val fieldCount: Int get() = items.size

    override val isEmpty: Boolean get() = items.isEmpty()

    override fun clear() {
        items.clear()
    }

    override val artworks: List<Artwork>
        get() = items.filterIsInstance<Mp4Item.Cover>().map {
            Artwork(data = it.data, mimeType = if (it.isPng) "image/png" else "image/jpeg")
        }

    override fun addArtwork(artwork: Artwork) {
        items += Mp4Item.Cover(artwork.data, isPng = artwork.mimeType.lowercase().contains("png"))
    }

    override fun clearArtworks() {
        items.removeAll { it is Mp4Item.Cover }
    }

    override fun toString(): String = "Mp4Tag($items)"
}
