package org.jaudiotagger.kt.tag.id3

/**
 * Decoded ID3v2 frame content. Frame ids are stored in the tag-version's own
 * vocabulary (e.g. "TAL" for v2.2, "TALB" for v2.3/2.4).
 */
sealed class Id3v2Frame(val id: String) {

    /** TRCK/TPOS (TRK/TPA in v2.2): track or disc number plus optional total. */
    class NumberTotal(id: String, val part: PartOfSetValue) : Id3v2Frame(id) {
        override fun toString(): String = "$id=$part"
    }

    /** T*** text frame; may carry several NUL-separated values. */
    class Text(id: String, val values: MutableList<String>) : Id3v2Frame(id) {
        val value: String get() = values.firstOrNull() ?: ""
        override fun toString(): String = "$id=$values"
    }

    /** TXXX user-defined text. */
    class UserText(id: String, val description: String, var value: String) : Id3v2Frame(id) {
        override fun toString(): String = "$id[$description]=$value"
    }

    /** W*** URL frame. */
    class Url(id: String, var url: String) : Id3v2Frame(id) {
        override fun toString(): String = "$id=$url"
    }

    /** WXXX user-defined URL. */
    class UserUrl(id: String, val description: String, var url: String) : Id3v2Frame(id) {
        override fun toString(): String = "$id[$description]=$url"
    }

    /** COMM comment / USLT lyrics: language + description + text. */
    class LanguageText(
        id: String,
        val language: String,
        val description: String,
        var text: String,
    ) : Id3v2Frame(id) {
        override fun toString(): String = "$id[$language,$description]=$text"
    }

    /** APIC / PIC attached picture. */
    class Picture(
        id: String,
        val mimeType: String,
        val pictureType: Int,
        val description: String,
        val data: ByteArray,
    ) : Id3v2Frame(id) {
        override fun toString(): String = "$id[$mimeType,type=$pictureType,${data.size} bytes]"
    }

    /** UFID unique file identifier. */
    class UniqueFileId(id: String, val owner: String, val data: ByteArray) : Id3v2Frame(id) {
        override fun toString(): String = "$id[$owner]"
    }

    /** POPM popularimeter. */
    class Popularimeter(id: String, val email: String, val rating: Int, val counter: Long) : Id3v2Frame(id) {
        override fun toString(): String = "$id[$email]=$rating"
    }

    /** IPLS / TIPL / TMCL involved-people list: (role, person) pairs. */
    class PairedText(id: String, val pairs: MutableList<Pair<String, String>>) : Id3v2Frame(id) {
        override fun toString(): String = "$id=$pairs"
    }

    /** Any frame the library does not interpret; raw body kept for round trips. */
    class Unknown(id: String, val rawBody: ByteArray) : Id3v2Frame(id) {
        override fun toString(): String = "$id(<${rawBody.size} bytes>)"
    }
}
