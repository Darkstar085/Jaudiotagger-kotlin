package org.jaudiotagger.kt.tag.vorbiscomment

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jaudiotagger.kt.audio.flac.FlacPictureCodec
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag

/**
 * A single `NAME=value` comment. Names are case-insensitive; the original spelling is
 * kept so rewriting an untouched tag does not change the packet bytes.
 */
class VorbisCommentField(val id: String, val value: String) {

    override fun toString(): String = "$id=$value"

    companion object {
        /** Id assigned to malformed comments that lack the `=` separator. */
        const val ERRONEOUS_ID = "ERRONEOUS"

        /** Parses the raw `NAME=value` form found in the file. */
        fun parse(raw: String): VorbisCommentField {
            val i = raw.indexOf('=')
            return if (i == -1) {
                VorbisCommentField(ERRONEOUS_ID, raw)
            } else {
                VorbisCommentField(raw.substring(0, i), raw.substring(i + 1))
            }
        }
    }
}

/**
 * Vorbis Comment tag, used by FLAC and Ogg Vorbis/Opus.
 *
 * Fields keep file order. The vendor string is kept separate from user comments,
 * as in the binary format; [FieldKey.ENCODER] reads and writes it.
 *
 * Artwork is not stored here: FLAC keeps pictures in separate metadata blocks and
 * Ogg embeds them as base64 `METADATA_BLOCK_PICTURE` comments, so the containing
 * tag ([org.jaudiotagger.kt.tag.flac.FlacTag] for FLAC) implements the artwork API.
 */
class VorbisCommentTag(
    var vendor: String = DEFAULT_VENDOR,
) : Tag {

    private val fields = mutableListOf<VorbisCommentField>()

    val allFields: List<VorbisCommentField> get() = fields

    // ---- string-keyed access (also usable for custom/unmapped field names) ----

    fun firstRaw(id: String): String? {
        return fields.firstOrNull { field -> field.id.equals(id, ignoreCase = true) }?.value
    }

    fun allRaw(id: String): List<String> {
        return fields.filter { field -> field.id.equals(id, ignoreCase = true) }.map { field -> field.value }
    }

    fun addRaw(id: String, value: String) {
        fields += VorbisCommentField(id, value)
    }

    fun setRaw(id: String, value: String) {
        val insertAt = fields.indexOfFirst { field -> field.id.equals(id, ignoreCase = true) }
        fields.removeAll { field -> field.id.equals(id, ignoreCase = true) }
        if (insertAt >= 0) {
            fields.add(insertAt, VorbisCommentField(id, value))
        } else {
            fields += VorbisCommentField(id, value)
        }
    }

    fun removeRaw(id: String) {
        fields.removeAll { field -> field.id.equals(id, ignoreCase = true) }
    }

    internal fun addParsedField(field: VorbisCommentField) {
        fields += field
    }

    // ---- FieldKey access ----

    private fun vorbisKey(key: FieldKey): VorbisCommentFieldKey =
        fieldKeyToVorbisKey[key]
            ?: throw IllegalArgumentException("FieldKey $key is not supported by VorbisComment")

    override fun first(key: FieldKey): String? {
        return all(key).firstOrNull()
    }

    override fun all(key: FieldKey): List<String> {
        return when (key) {
            FieldKey.ENCODER -> if (vendor.isEmpty()) emptyList() else listOf(vendor)
            FieldKey.ALBUM_ARTIST -> albumArtistValues()
            FieldKey.TRACK -> numberValues(TRACK_FIELDS)
            FieldKey.DISC_NO -> numberValues(DISC_FIELDS)
            FieldKey.TRACK_TOTAL -> totalValues(TRACK_FIELDS)
            FieldKey.DISC_TOTAL -> totalValues(DISC_FIELDS)
            else -> allRaw(vorbisKey(key).fieldName)
        }
    }

    override fun set(key: FieldKey, value: String) {
        when (key) {
            FieldKey.ENCODER -> vendor = value
            FieldKey.ALBUM_ARTIST -> {
                setRaw(VorbisCommentFieldKey.ALBUMARTIST.fieldName, value)
                removeRaw(VorbisCommentFieldKey.ALBUMARTIST_JRIVER.fieldName)
            }
            FieldKey.TRACK -> setNumber(TRACK_FIELDS, value)
            FieldKey.DISC_NO -> setNumber(DISC_FIELDS, value)
            FieldKey.TRACK_TOTAL -> setTotal(TRACK_FIELDS, value)
            FieldKey.DISC_TOTAL -> setTotal(DISC_FIELDS, value)
            else -> setRaw(vorbisKey(key).fieldName, value)
        }
    }

    override fun add(key: FieldKey, value: String) {
        if (key == FieldKey.ENCODER) {
            vendor = value
            return
        }
        if (key == FieldKey.ALBUM_ARTIST) {
            addRaw(VorbisCommentFieldKey.ALBUMARTIST.fieldName, value)
            return
        }
        addRaw(vorbisKey(key).fieldName, value)
    }

    override fun remove(key: FieldKey) {
        when (key) {
            FieldKey.ENCODER -> vendor = DEFAULT_VENDOR
            FieldKey.ALBUM_ARTIST -> {
                removeRaw(VorbisCommentFieldKey.ALBUMARTIST.fieldName)
                removeRaw(VorbisCommentFieldKey.ALBUMARTIST_JRIVER.fieldName)
            }
            FieldKey.TRACK -> removeNumber(TRACK_FIELDS)
            FieldKey.DISC_NO -> removeNumber(DISC_FIELDS)
            FieldKey.TRACK_TOTAL -> removeTotal(TRACK_FIELDS)
            FieldKey.DISC_TOTAL -> removeTotal(DISC_FIELDS)
            else -> removeRaw(vorbisKey(key).fieldName)
        }
    }

    /** Java default: read ALBUMARTIST, then JRiver's `ALBUM ARTIST`. */
    private fun albumArtistValues(): List<String> {
        val standard = allRaw(VorbisCommentFieldKey.ALBUMARTIST.fieldName)
        if (standard.isNotEmpty()) return standard
        return allRaw(VorbisCommentFieldKey.ALBUMARTIST_JRIVER.fieldName)
    }

    // ffmpeg writes "TRACKNUMBER=3/12"; the number and total fields read it like ID3 TRCK.
    private fun numberValues(fields: NumberTotalFields): List<String> {
        return allRaw(fields.number).map { value -> parseNumberTotal(value)?.number ?: value }
    }

    private fun totalValues(fields: NumberTotalFields): List<String> {
        val totals = allRaw(fields.total)
        if (totals.isNotEmpty()) {
            return totals
        }
        val combined = combinedNumber(fields) ?: return emptyList()
        return listOf(combined.total)
    }

    private fun setNumber(fields: NumberTotalFields, value: String) {
        val combined = combinedNumber(fields)
        if (combined != null && isPlainNumber(value)) {
            setRaw(fields.number, "$value/${combined.total}")
        } else {
            setRaw(fields.number, value)
        }
    }

    private fun setTotal(fields: NumberTotalFields, value: String) {
        val combined = combinedNumber(fields)
        if (combined != null) {
            setRaw(fields.number, "${combined.number}/$value")
        } else {
            setRaw(fields.total, value)
        }
    }

    private fun removeNumber(fields: NumberTotalFields) {
        val combined = combinedNumber(fields)
        removeRaw(fields.number)
        if (combined != null) {
            addRaw(fields.total, combined.total)
        }
    }

    private fun removeTotal(fields: NumberTotalFields) {
        removeRaw(fields.total)
        val combined = combinedNumber(fields) ?: return
        setRaw(fields.number, combined.number)
    }

    /** The first number field in `N/M` form, when no separate total field overrides it. */
    private fun combinedNumber(fields: NumberTotalFields): NumberTotal? {
        if (firstRaw(fields.total) != null) {
            return null
        }
        val number = firstRaw(fields.number) ?: return null
        return parseNumberTotal(number)
    }

    private fun parseNumberTotal(value: String): NumberTotal? {
        val match = NUMBER_WITH_TOTAL.matchEntire(value) ?: return null
        return NumberTotal(match.groupValues[1], match.groupValues[2])
    }

    private fun isPlainNumber(value: String): Boolean {
        return value.isNotEmpty() && value.all { char -> char in '0'..'9' }
    }

    private class NumberTotalFields(val number: String, val total: String)

    private class NumberTotal(val number: String, val total: String)

    override val fieldCount: Int get() = fields.size

    override val isEmpty: Boolean get() = fields.isEmpty()

    override fun clear() {
        fields.clear()
    }

    // Artwork is carried as base64-encoded FLAC picture blocks in
    // METADATA_BLOCK_PICTURE comments (plus the legacy COVERART field), which is
    // how Ogg embeds pictures. FLAC files use separate PICTURE metadata blocks
    // instead — FlacTag overrides the artwork API accordingly.
    @OptIn(ExperimentalEncodingApi::class)
    override val artworks: List<Artwork>
        get() {
            val result = mutableListOf<Artwork>()
            for (encoded in allRaw(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE.fieldName)) {
                try {
                    result += FlacPictureCodec.decode(Base64.decode(encoded))
                } catch (_: Exception) {
                    // unparsable picture comment: ignore, the rest of the tag is fine
                }
            }
            // legacy pre-METADATA_BLOCK_PICTURE style
            val legacyData = firstRaw(VorbisCommentFieldKey.COVERART.fieldName)
            if (legacyData != null) {
                try {
                    result += Artwork(
                        data = Base64.decode(legacyData),
                        mimeType = firstRaw(VorbisCommentFieldKey.COVERARTMIME.fieldName) ?: "",
                    )
                } catch (_: Exception) {
                }
            }
            return result
        }

    @OptIn(ExperimentalEncodingApi::class)
    override fun addArtwork(artwork: Artwork) {
        addRaw(
            VorbisCommentFieldKey.METADATA_BLOCK_PICTURE.fieldName,
            Base64.encode(FlacPictureCodec.encode(artwork)),
        )
    }

    override fun clearArtworks() {
        removeRaw(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE.fieldName)
        removeRaw(VorbisCommentFieldKey.COVERART.fieldName)
        removeRaw(VorbisCommentFieldKey.COVERARTMIME.fieldName)
    }

    override fun toString(): String = "VorbisCommentTag(vendor=$vendor, fields=$fields)"

    companion object {
        const val DEFAULT_VENDOR = "jaudiotagger"

        private val TRACK_FIELDS = NumberTotalFields(
            VorbisCommentFieldKey.TRACKNUMBER.fieldName,
            VorbisCommentFieldKey.TRACKTOTAL.fieldName,
        )
        private val DISC_FIELDS = NumberTotalFields(
            VorbisCommentFieldKey.DISCNUMBER.fieldName,
            VorbisCommentFieldKey.DISCTOTAL.fieldName,
        )
        private val NUMBER_WITH_TOTAL = Regex("""\s*([0-9]+)\s*/\s*([0-9]+)\s*""")
    }
}
