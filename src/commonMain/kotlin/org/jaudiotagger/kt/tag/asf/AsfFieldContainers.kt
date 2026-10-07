package org.jaudiotagger.kt.tag.asf

import org.jaudiotagger.kt.audio.asf.AsfContainerType

/**
 * Highest ASF container per descriptor name. Ported from
 * [org.jaudiotagger.tag.asf.AsfFieldKey].
 */
internal object AsfFieldContainers {

    private val contentDescription = setOf("TITLE", "AUTHOR", "COPYRIGHT", "DESCRIPTION", "RATING")

    /** Fields that may repeat within multi-valued containers. */
    private val multiValuedNames = setOf(
        "WM/AlbumArtist",
        "ALBUM_ARTISTS",
        "ALBUM_ARTISTS_SORT",
        "ALBUM_COMPOSER",
        "ALBUM_COMPOSER_SORT",
        "WM/ARTISTS",
        "WM/ARTISTS_SORT",
        "ARRANGER_SORT",
        "AUDIO_ENGINEER",
        "AUDIO_ENGINEER_SORT",
        "BALANCE_ENGINEER",
        "BALANCE_ENGINEER_SORT",
        "WM/Category",
        "CHOIR",
        "CHOIR_SORT",
        "CLASSICAL_CATALOG",
        "CLASSICAL_NICKNAME",
        "WM/Composer",
        "CONDUCTOR_SORT",
        "CREDITS",
        "CUSTOM1",
        "CUSTOM2",
        "CUSTOM3",
        "CUSTOM4",
        "CUSTOM5",
        "WM/Director",
        "DJMIXER_SORT",
        "ENGINEER_SORT",
        "ENSEMBLE",
        "ENSEMBLE_SORT",
        "FBPM",
        "WM/Genre",
        "WM/GenreID",
        "WM/InvolvedPerson",
        "INSTRUMENT",
        "LYRICIST_SORT",
        "MASTERING",
        "MASTERING_SORT",
        "MIXER_SORT",
        "MOOD_ACOUSTIC",
        "MOOD_AGGRESSIVE",
        "MOOD_AROUSAL",
        "MOOD_DANCEABILITY",
        "MOOD_ELECTRONIC",
        "MOOD_HAPPY",
        "MOOD_INSTRUMENTAL",
        "MOOD_PARTY",
        "MOOD_RELAXED",
        "MOOD_SAD",
        "MOOD_VALENCE",
        "ORCHESTRA",
        "ORCHESTRA_SORT",
        "PERFORMER",
        "PERFORMER_NAME",
        "PERFORMER_NAME_SORT",
        "PRODUCER_SORT",
        "RECORDING_ENGINEER",
        "RECORDING_ENGINEER_SORT",
        "SOUND_ENGINEER",
        "SOUND_ENGINEER_SORT",
        "WM/Picture",
        "WM/AlbumCoverURL",
        "WM/Tags",
        "___CUSTOM___",
    )

    fun highestContainer(name: String): AsfContainerType {
        val upper = name.uppercase()
        if (upper in contentDescription) return AsfContainerType.CONTENT_DESCRIPTION
        return AsfContainerType.METADATA_LIBRARY
    }

    fun isMultiValued(name: String): Boolean =
        multiValuedNames.any { it.equals(name, ignoreCase = true) } ||
                name.startsWith("CUSTOM", ignoreCase = true)
}
