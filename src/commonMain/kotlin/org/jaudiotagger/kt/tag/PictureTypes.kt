package org.jaudiotagger.kt.tag

/**
 * Picture types defined by ID3v2 APIC and reused by FLAC/Vorbis picture blocks.
 */
object PictureTypes {
    const val DEFAULT_ID = 3 // Cover (front)

    val names: List<String> = listOf(
        "Other",
        "32x32 pixels 'file icon' (PNG only)",
        "Other file icon",
        "Cover (front)",
        "Cover (back)",
        "Leaflet page",
        "Media (e.g. label side of CD)",
        "Lead artist/lead performer/soloist",
        "Artist/performer",
        "Conductor",
        "Band/Orchestra",
        "Composer",
        "Lyricist/text writer",
        "Recording Location",
        "During recording",
        "During performance",
        "Movie/video screen capture",
        "A bright coloured fish",
        "Illustration",
        "Band/artist logotype",
        "Publisher/Studio logotype",
    )

    val size: Int get() = names.size

    fun nameOf(id: Int): String? = names.getOrNull(id)
}
