package org.jaudiotagger.kt.tag

/**
 * Format-independent view of an audio file's metadata.
 *
 * Text fields are addressed by [FieldKey]; each implementation maps keys to its
 * native field names. Multi-value fields (e.g. several genres) are supported by
 * [all] and [add].
 */
interface Tag {

    /** First value for [key], or null when the field is absent. */
    fun first(key: FieldKey): String?

    /** All values for [key], in file order; empty when absent. */
    fun all(key: FieldKey): List<String>

    /** Replaces all values of [key] with [value]. */
    fun set(key: FieldKey, value: String)

    /** Appends an additional [value] to [key]. */
    fun add(key: FieldKey, value: String)

    /** Removes all values of [key]. */
    fun remove(key: FieldKey)

    fun has(key: FieldKey): Boolean = first(key) != null

    /** Number of fields including artwork entries. */
    val fieldCount: Int

    val isEmpty: Boolean

    /** Removes every field including artwork. */
    fun clear()

    val artworks: List<Artwork>

    fun addArtwork(artwork: Artwork)

    /** Replaces all existing artwork with [artwork]. */
    fun setArtwork(artwork: Artwork) {
        clearArtworks()
        addArtwork(artwork)
    }

    fun clearArtworks()
}
