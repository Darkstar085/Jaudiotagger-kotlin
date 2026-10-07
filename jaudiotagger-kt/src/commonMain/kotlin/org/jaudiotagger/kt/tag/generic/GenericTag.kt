package org.jaudiotagger.kt.tag.generic

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag

/**
 * Simple in-memory tag for formats without a rich native tag structure
 * (e.g. RealAudio's CONT chunk). Stores plain key/value pairs, no artwork.
 */
class GenericTag : Tag {

    private val fields = mutableMapOf<FieldKey, MutableList<String>>()

    override fun first(key: FieldKey): String? = fields[key]?.firstOrNull()

    override fun all(key: FieldKey): List<String> = fields[key] ?: emptyList()

    override fun set(key: FieldKey, value: String) {
        fields[key] = mutableListOf(value)
    }

    override fun add(key: FieldKey, value: String) {
        fields.getOrPut(key) { mutableListOf() } += value
    }

    override fun remove(key: FieldKey) {
        fields.remove(key)
    }

    override val fieldCount: Int get() = fields.values.sumOf { it.size }

    override val isEmpty: Boolean get() = fields.isEmpty()

    override fun clear() {
        fields.clear()
    }

    override val artworks: List<Artwork> get() = emptyList()

    override fun addArtwork(artwork: Artwork) {
        throw UnsupportedOperationException("This tag format does not support artwork")
    }

    override fun clearArtworks() {}

    override fun toString(): String = "GenericTag($fields)"
}
