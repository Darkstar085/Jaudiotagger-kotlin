package org.jaudiotagger.kt.tag.id3

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag

/**
 * ID3v1(.1) tag: fixed 128 bytes at the end of the file.
 *
 * Supports title, artist, album, year, comment, genre and (v1.1) track only.
 * Reading an unsupported [FieldKey] yields nothing; writing one throws.
 */
class Id3v1Tag : Tag {

    var title: String = ""
    var artist: String = ""
    var album: String = ""
    var year: String = ""
    var comment: String = ""

    /** Genre name resolved via [GenreTypes]; empty when unset or unknown. */
    var genre: String = ""

    /** Track number (ID3v1.1); null when absent. */
    var track: Int? = null

    private fun valueOf(key: FieldKey): String? = when (key) {
        FieldKey.TITLE -> title
        FieldKey.ARTIST -> artist
        FieldKey.ALBUM -> album
        FieldKey.YEAR -> year
        FieldKey.COMMENT -> comment
        FieldKey.GENRE -> genre
        FieldKey.TRACK -> track?.toString()
        else -> null
    }?.ifEmpty { null }

    override fun first(key: FieldKey): String? = valueOf(key)

    override fun all(key: FieldKey): List<String> = listOfNotNull(valueOf(key))

    override fun set(key: FieldKey, value: String) {
        when (key) {
            FieldKey.TITLE -> title = value
            FieldKey.ARTIST -> artist = value
            FieldKey.ALBUM -> album = value
            FieldKey.YEAR -> year = value
            FieldKey.COMMENT -> comment = value
            FieldKey.GENRE -> genre = value
            FieldKey.TRACK -> track = value.toIntOrNull()
            else -> throw IllegalArgumentException("FieldKey $key is not supported by ID3v1")
        }
    }

    override fun add(key: FieldKey, value: String) = set(key, value)

    override fun remove(key: FieldKey) {
        when (key) {
            FieldKey.TITLE -> title = ""
            FieldKey.ARTIST -> artist = ""
            FieldKey.ALBUM -> album = ""
            FieldKey.YEAR -> year = ""
            FieldKey.COMMENT -> comment = ""
            FieldKey.GENRE -> genre = ""
            FieldKey.TRACK -> track = null
            else -> {}
        }
    }

    override val fieldCount: Int
        get() = listOf(
            FieldKey.TITLE, FieldKey.ARTIST, FieldKey.ALBUM, FieldKey.YEAR,
            FieldKey.COMMENT, FieldKey.GENRE, FieldKey.TRACK,
        ).count { valueOf(it) != null }

    override val isEmpty: Boolean get() = fieldCount == 0

    override fun clear() {
        title = ""; artist = ""; album = ""; year = ""; comment = ""; genre = ""
        track = null
    }

    override val artworks: List<Artwork> get() = emptyList()

    override fun addArtwork(artwork: Artwork) {
        throw UnsupportedOperationException("ID3v1 does not support artwork")
    }

    override fun clearArtworks() {}

    override fun toString(): String =
        "Id3v1Tag(title=$title, artist=$artist, album=$album, year=$year, " +
            "comment=$comment, genre=$genre, track=$track)"
}
