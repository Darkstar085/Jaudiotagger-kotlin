package org.jaudiotagger.kt.tag.wav

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version

/**
 * WAV metadata: an ID3v2 chunk (primary) plus the legacy RIFF LIST-INFO chunk.
 *
 * Reads prefer ID3 and fall back to INFO. Writes go to the ID3 tag and are
 * mirrored into the INFO fields that have a RIFF equivalent, so both chunks
 * stay in sync on save (the original's SAVE_BOTH_AND_SYNC behaviour).
 */
class WavTag(
    val id3: Id3v2Tag = Id3v2Tag(Id3v2Version.V23),
) : Tag {

    /** Raw LIST-INFO items by their 4-char RIFF id (e.g. "IART"). */
    val infoFields = LinkedHashMap<String, String>()

    private fun infoIdFor(key: FieldKey): String? = infoIdentifiers[key]

    override fun first(key: FieldKey): String? =
        id3.first(key) ?: infoIdFor(key)?.let { infoFields[it] }

    override fun all(key: FieldKey): List<String> {
        val fromId3 = id3.all(key)
        if (fromId3.isNotEmpty()) return fromId3
        return listOfNotNull(infoIdFor(key)?.let { infoFields[it] })
    }

    override fun set(key: FieldKey, value: String) {
        id3.set(key, value)
        infoIdFor(key)?.let { infoFields[it] = value }
    }

    override fun add(key: FieldKey, value: String) {
        id3.add(key, value)
        infoIdFor(key)?.let { infoFields[it] = all(key).joinToString(";") }
    }

    override fun remove(key: FieldKey) {
        id3.remove(key)
        infoIdFor(key)?.let { infoFields.remove(it) }
    }

    override val fieldCount: Int get() = id3.fieldCount + infoFields.size

    override val isEmpty: Boolean get() = id3.isEmpty && infoFields.isEmpty()

    override fun clear() {
        id3.clear()
        infoFields.clear()
    }

    override val artworks: List<Artwork> get() = id3.artworks

    override fun addArtwork(artwork: Artwork) = id3.addArtwork(artwork)

    override fun clearArtworks() = id3.clearArtworks()

    override fun toString(): String = "WavTag(id3=$id3, info=$infoFields)"

    companion object {
        /** RIFF LIST-INFO ids for common fields (from WavInfoIdentifier). */
        internal val infoIdentifiers: Map<FieldKey, String> = mapOf(
            FieldKey.ARTIST to "IART",
            FieldKey.ALBUM to "IPRD",
            FieldKey.TITLE to "INAM",
            FieldKey.TRACK to "ITRK",
            FieldKey.YEAR to "ICRD",
            FieldKey.GENRE to "IGNR",
            FieldKey.ALBUM_ARTIST to "iaar",
            FieldKey.COMMENT to "ICMT",
            FieldKey.COMPOSER to "IMUS",
            FieldKey.CONDUCTOR to "ITCH",
            FieldKey.LYRICIST to "IWRI",
            FieldKey.ENCODER to "ISFT",
            FieldKey.RATING to "IRTD",
            FieldKey.ISRC to "ISRC",
            FieldKey.RECORD_LABEL to "ICMS",
            FieldKey.COPYRIGHT to "ICOP",
        )
    }
}
