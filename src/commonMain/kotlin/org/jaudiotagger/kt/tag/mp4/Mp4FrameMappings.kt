package org.jaudiotagger.kt.tag.mp4

import org.jaudiotagger.kt.tag.FieldKey

/**
 * Where a [FieldKey] lives in an MP4 ilst box: either a plain atom (e.g. ©nam)
 * or a reverse-DNS "----" atom addressed by issuer + identifier.
 *
 * Generated from Mp4FieldKey/Mp4Tag in the Java sources; do not edit.
 */
class Mp4FrameKey(val atomId: String, val issuer: String? = null, val identifier: String? = null)

internal val mp4FrameKeys: Map<FieldKey, Mp4FrameKey> = mapOf(
    FieldKey.ACOUSTID_FINGERPRINT to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "Acoustid Fingerprint"
    ),
    FieldKey.ACOUSTID_ID to Mp4FrameKey("----", "com.apple.iTunes", "Acoustid Id"),
    FieldKey.ALBUM to Mp4FrameKey("©alb"),
    FieldKey.ALBUM_ARTIST to Mp4FrameKey("aART"),
    FieldKey.ALBUM_ARTIST_SORT to Mp4FrameKey("soaa"),
    FieldKey.ALBUM_ARTISTS to Mp4FrameKey("----", "com.apple.iTunes", "ALBUM_ARTISTS"),
    FieldKey.ALBUM_ARTISTS_SORT to Mp4FrameKey("----", "com.apple.iTunes", "ALBUM_ARTISTS_SORT"),
    FieldKey.ALBUM_COMPOSER to Mp4FrameKey("----", "com.apple.iTunes", "ALBUM_COMPOSER"),
    FieldKey.ALBUM_COMPOSER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "ALBUM_COMPOSER_SORT"),
    FieldKey.ALBUM_SORT to Mp4FrameKey("soal"),
    FieldKey.ALBUM_YEAR to Mp4FrameKey("----", "com.apple.iTunes", "ALBUM_YEAR"),
    FieldKey.AMAZON_ID to Mp4FrameKey("----", "com.apple.iTunes", "ASIN"),
    FieldKey.ARRANGER to Mp4FrameKey("----", "com.apple.iTunes", "ARRANGER"),
    FieldKey.ARRANGER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "ARRANGER_SORT"),
    FieldKey.ARTIST to Mp4FrameKey("©ART"),
    FieldKey.ARTISTS to Mp4FrameKey("----", "com.apple.iTunes", "ARTISTS"),
    FieldKey.ARTIST_SORT to Mp4FrameKey("soar"),
    FieldKey.ARTISTS_SORT to Mp4FrameKey("----", "com.apple.iTunes", "ARTISTS_SORT"),
    FieldKey.AUDIO_ENGINEER to Mp4FrameKey("----", "com.apple.iTunes", "AUDIO_ENGINEER"),
    FieldKey.AUDIO_ENGINEER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "AUDIO_ENGINEER_SORT"),
    FieldKey.BALANCE_ENGINEER to Mp4FrameKey("----", "com.apple.iTunes", "BALANCE_ENGINEER"),
    FieldKey.BALANCE_ENGINEER_SORT to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "BALANCE_ENGINEER_SORT"
    ),
    FieldKey.BARCODE to Mp4FrameKey("----", "com.apple.iTunes", "BARCODE"),
    FieldKey.BPM to Mp4FrameKey("tmpo"),
    FieldKey.CATALOG_NO to Mp4FrameKey("----", "com.apple.iTunes", "CATALOGNUMBER"),
    FieldKey.CHOIR to Mp4FrameKey("----", "com.apple.iTunes", "CHOR"),
    FieldKey.CHOIR_SORT to Mp4FrameKey("----", "com.apple.iTunes", "CHOIR_SORT"),
    FieldKey.CLASSICAL_CATALOG to Mp4FrameKey("----", "com.apple.iTunes", "CLASSICAL_CATALOG"),
    FieldKey.CLASSICAL_NICKNAME to Mp4FrameKey("----", "com.apple.iTunes", "CLASSICAL_NICKNAME"),
    FieldKey.COMMENT to Mp4FrameKey("©cmt"),
    FieldKey.COMPOSER to Mp4FrameKey("©wrt"),
    FieldKey.COMPOSER_SORT to Mp4FrameKey("soco"),
    FieldKey.CONDUCTOR to Mp4FrameKey("----", "com.apple.iTunes", "CONDUCTOR"),
    FieldKey.COUNTRY to Mp4FrameKey("----", "com.apple.iTunes", "Country"),
    FieldKey.CONDUCTOR_SORT to Mp4FrameKey("----", "com.apple.iTunes", "CONDUCTOR_SORT"),
    FieldKey.COPYRIGHT to Mp4FrameKey("cprt"),
    FieldKey.COVER_ART to Mp4FrameKey("covr"),
    FieldKey.CREDITS to Mp4FrameKey("----", "com.apple.iTunes", "CREDITS"),
    FieldKey.CUSTOM1 to Mp4FrameKey("----", "com.apple.iTunes", "CUSTOM1"),
    FieldKey.CUSTOM2 to Mp4FrameKey("----", "com.apple.iTunes", "CUSTOM2"),
    FieldKey.CUSTOM3 to Mp4FrameKey("----", "com.apple.iTunes", "CUSTOM3"),
    FieldKey.CUSTOM4 to Mp4FrameKey("----", "com.apple.iTunes", "CUSTOM4"),
    FieldKey.CUSTOM5 to Mp4FrameKey("----", "com.apple.iTunes", "CUSTOM5"),
    FieldKey.DISC_NO to Mp4FrameKey("disk"),
    FieldKey.DISC_SUBTITLE to Mp4FrameKey("----", "com.apple.iTunes", "DISCSUBTITLE"),
    FieldKey.DISC_TOTAL to Mp4FrameKey("disk"),
    FieldKey.DJMIXER to Mp4FrameKey("----", "com.apple.iTunes", "DJMIXER"),
    FieldKey.DJMIXER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "DJMIXER_SORT"),
    FieldKey.MOOD_ELECTRONIC to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_ELECTRONIC"),
    FieldKey.ENCODER to Mp4FrameKey("©too"),
    FieldKey.ENGINEER to Mp4FrameKey("----", "com.apple.iTunes", "ENGINEER"),
    FieldKey.ENGINEER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "ENGINEER_SORT"),
    FieldKey.ENSEMBLE to Mp4FrameKey("----", "com.apple.iTunes", "Ensemble"),
    FieldKey.ENSEMBLE_SORT to Mp4FrameKey("----", "com.apple.iTunes", "Ensemble Sort"),
    FieldKey.FBPM to Mp4FrameKey("----", "com.apple.iTunes", "fBPM"),
    FieldKey.GENRE to Mp4FrameKey("gnre"),
    FieldKey.GROUP to Mp4FrameKey("----", "com.apple.iTunes", "GROUP"),
    FieldKey.GROUPING to Mp4FrameKey("©grp"),
    FieldKey.INSTRUMENT to Mp4FrameKey("----", "com.apple.iTunes", "INSTRUMENT"),
    FieldKey.INVOLVEDPEOPLE to Mp4FrameKey("----", "com.apple.iTunes", "involvedpeople"),
    FieldKey.IPI to Mp4FrameKey("----", "com.apple.iTunes", "IPI"),
    FieldKey.ISRC to Mp4FrameKey("----", "com.apple.iTunes", "ISRC"),
    FieldKey.ISWC to Mp4FrameKey("----", "com.apple.iTunes", "ISWC"),
    FieldKey.IS_COMPILATION to Mp4FrameKey("cpil"),
    FieldKey.IS_CLASSICAL to Mp4FrameKey("----", "com.apple.iTunes", "IS_CLASSICAL"),
    FieldKey.IS_GREATEST_HITS to Mp4FrameKey("----", "com.apple.iTunes", "IS_GREATEST_HITS"),
    FieldKey.IS_HD to Mp4FrameKey("----", "com.apple.iTunes", "IS_HD"),
    FieldKey.IS_SOUNDTRACK to Mp4FrameKey("----", "com.apple.iTunes", "IS_SOUNDTRACK"),
    FieldKey.JAIKOZ_ID to Mp4FrameKey("----", "com.apple.iTunes", "JAIKOZ_ID"),
    FieldKey.KEY to Mp4FrameKey("----", "com.apple.iTunes", "initialkey"),
    FieldKey.LANGUAGE to Mp4FrameKey("----", "com.apple.iTunes", "LANGUAGE"),
    FieldKey.LYRICIST to Mp4FrameKey("----", "com.apple.iTunes", "LYRICIST"),
    FieldKey.LYRICIST_SORT to Mp4FrameKey("----", "com.apple.iTunes", "LYRICIST_SORT"),
    FieldKey.LYRICS to Mp4FrameKey("©lyr"),
    FieldKey.MEDIA to Mp4FrameKey("----", "com.apple.iTunes", "MEDIA"),
    FieldKey.MIXER to Mp4FrameKey("----", "com.apple.iTunes", "MIXER"),
    FieldKey.MIXER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "MIXER_SORT"),
    FieldKey.MOOD to Mp4FrameKey("----", "com.apple.iTunes", "MOOD"),
    FieldKey.MOOD_ACOUSTIC to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_ACOUSTIC"),
    FieldKey.MOOD_AGGRESSIVE to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_AGGRESSIVE"),
    FieldKey.MOOD_AROUSAL to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_AROUSAL"),
    FieldKey.MOOD_DANCEABILITY to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_DANCEABILITY"),
    FieldKey.MOOD_HAPPY to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_HAPPY"),
    FieldKey.MOOD_INSTRUMENTAL to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_INSTRUMENTAL"),
    FieldKey.MOOD_PARTY to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_PARTY"),
    FieldKey.MOOD_RELAXED to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_RELAXED"),
    FieldKey.MOOD_SAD to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_SAD"),
    FieldKey.MOOD_VALENCE to Mp4FrameKey("----", "com.apple.iTunes", "MOOD_VALENCE"),
    FieldKey.MOVEMENT to Mp4FrameKey("©mvn"),
    FieldKey.MOVEMENT_NO to Mp4FrameKey("©mvi"),
    FieldKey.MOVEMENT_TOTAL to Mp4FrameKey("©mvc"),
    FieldKey.MUSICBRAINZ_WORK to Mp4FrameKey("----", "com.apple.iTunes", "MUSICBRAINZ_WORK"),
    FieldKey.MUSICBRAINZ_ARTISTID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Artist Id"
    ),
    FieldKey.MUSICBRAINZ_DISC_ID to Mp4FrameKey("----", "com.apple.iTunes", "MusicBrainz Disc Id"),
    FieldKey.MUSICBRAINZ_ORIGINAL_RELEASE_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Original Album Id"
    ),
    FieldKey.MUSICBRAINZ_RELEASEARTISTID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Album Artist Id"
    ),
    FieldKey.MUSICBRAINZ_RELEASEID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Album Id"
    ),
    FieldKey.MUSICBRAINZ_RELEASE_COUNTRY to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Album Release Country"
    ),
    FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Release Group Id"
    ),
    FieldKey.MUSICBRAINZ_RELEASE_STATUS to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Album Status"
    ),
    FieldKey.MUSICBRAINZ_RELEASE_TRACK_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Release Track Id"
    ),
    FieldKey.MUSICBRAINZ_RELEASE_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Album Type"
    ),
    FieldKey.MUSICBRAINZ_TRACK_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MusicBrainz Track Id"
    ),
    FieldKey.MUSICBRAINZ_WORK_ID to Mp4FrameKey("----", "com.apple.iTunes", "MusicBrainz Work Id"),
    FieldKey.MUSICBRAINZ_RECORDING_WORK_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_RECORDING_WORK_ID"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL1_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL1_ID"
    ),
    FieldKey.MUSICBRAINZ_RECORDING_WORK to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_RECORDING_WORK"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL1 to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL1"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL1_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL1_TYPE"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL2_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL2_ID"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL2 to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL2"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL2_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL2_TYPE"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL3_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL3_ID"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL3 to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL3"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL3_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL3_TYPE"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL4_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL4_ID"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL4 to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL4"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL4_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL4_TYPE"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL5_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL5_ID"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL5 to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL5"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL5_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL5_TYPE"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL6_ID to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL6_ID"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL6 to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL6"
    ),
    FieldKey.MUSICBRAINZ_WORK_PART_LEVEL6_TYPE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "MUSICBRAINZ_WORK_PART_LEVEL6_TYPE"
    ),
    FieldKey.MUSICIP_ID to Mp4FrameKey("----", "com.apple.iTunes", "MusicIP PUID"),
    FieldKey.OCCASION to Mp4FrameKey("----", "com.apple.iTunes", "OCCASION"),
    FieldKey.OPUS to Mp4FrameKey("----", "com.apple.iTunes", "OPUS"),
    FieldKey.ORCHESTRA to Mp4FrameKey("----", "com.apple.iTunes", "ORCHESTRA"),
    FieldKey.ORCHESTRA_SORT to Mp4FrameKey("----", "com.apple.iTunes", "ORCHESTRA_SORT"),
    FieldKey.ORIGINAL_ALBUM to Mp4FrameKey("----", "com.apple.iTunes", "ORIGINAL ALBUM"),
    FieldKey.ORIGINALRELEASEDATE to Mp4FrameKey("----", "com.apple.iTunes", "ORIGINALRELEASEDATE"),
    FieldKey.ORIGINAL_ARTIST to Mp4FrameKey("----", "com.apple.iTunes", "ORIGINAL ARTIST"),
    FieldKey.ORIGINAL_LYRICIST to Mp4FrameKey("----", "com.apple.iTunes", "ORIGINAL LYRICIST"),
    FieldKey.ORIGINAL_YEAR to Mp4FrameKey("----", "com.apple.iTunes", "ORIGINAL YEAR"),
    FieldKey.OVERALL_WORK to Mp4FrameKey("----", "com.apple.iTunes", "OVERALL_WORK"),
    FieldKey.PART to Mp4FrameKey("----", "com.apple.iTunes", "PART"),
    FieldKey.PART_NUMBER to Mp4FrameKey("----", "com.apple.iTunes", "PARTNUMBER"),
    FieldKey.PART_TYPE to Mp4FrameKey("----", "com.apple.iTunes", "PART_TYPE"),
    FieldKey.PERFORMER to Mp4FrameKey("----", "com.apple.iTunes", "Performer"),
    FieldKey.PERFORMER_NAME to Mp4FrameKey("----", "com.apple.iTunes", "PERFORMER_NAME"),
    FieldKey.PERFORMER_NAME_SORT to Mp4FrameKey("----", "com.apple.iTunes", "PERFORMER_NAME_SORT"),
    FieldKey.PERIOD to Mp4FrameKey("----", "com.apple.iTunes", "PERIOD"),
    FieldKey.PRODUCER to Mp4FrameKey("----", "com.apple.iTunes", "PRODUCER"),
    FieldKey.PRODUCER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "PRODUCER_SORT"),
    FieldKey.QUALITY to Mp4FrameKey("----", "com.apple.iTunes", "QUALITY"),
    FieldKey.RANKING to Mp4FrameKey("----", "com.apple.iTunes", "RANKING"),
    FieldKey.RATING to Mp4FrameKey("rate"),
    FieldKey.RECORD_LABEL to Mp4FrameKey("----", "com.apple.iTunes", "LABEL"),
    FieldKey.RECORDING_ENGINEER to Mp4FrameKey("----", "com.apple.iTunes", "RECORDING_ENGINEER"),
    FieldKey.RECORDING_ENGINEER_SORT to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "RECORDING_ENGINEER_SORT"
    ),
    FieldKey.REMIXER to Mp4FrameKey("----", "com.apple.iTunes", "REMIXER"),
    FieldKey.ROONALBUMTAG to Mp4FrameKey("----", "com.apple.iTunes", "ROONALBUMTAG"),
    FieldKey.ROONTRACKTAG to Mp4FrameKey("----", "com.apple.iTunes", "ROONTRACKTAG"),
    FieldKey.SCRIPT to Mp4FrameKey("----", "com.apple.iTunes", "SCRIPT"),
    FieldKey.SINGLE_DISC_TRACK_NO to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "SINGLE_DISC_TRACK_NO"
    ),
    FieldKey.SOUND_ENGINEER to Mp4FrameKey("----", "com.apple.iTunes", "SOUND_ENGINEER"),
    FieldKey.SOUND_ENGINEER_SORT to Mp4FrameKey("----", "com.apple.iTunes", "SOUND_ENGINEER_SORT"),
    FieldKey.SUBTITLE to Mp4FrameKey("----", "com.apple.iTunes", "SUBTITLE"),
    FieldKey.TAGS to Mp4FrameKey("----", "com.apple.iTunes", "TAGS"),
    FieldKey.TEMPO to Mp4FrameKey("empo"),
    FieldKey.TIMBRE to Mp4FrameKey("----", "com.apple.iTunes", "TIMBRE_BRIGHTNESS"),
    FieldKey.TITLE to Mp4FrameKey("©nam"),
    FieldKey.TITLE_MOVEMENT to Mp4FrameKey("----", "com.apple.iTunes", "TITLE_MOVEMENT"),
    FieldKey.TITLE_SORT to Mp4FrameKey("sonm"),
    FieldKey.TONALITY to Mp4FrameKey("----", "com.apple.iTunes", "TONALITY"),
    FieldKey.TRACK to Mp4FrameKey("trkn"),
    FieldKey.TRACK_TOTAL to Mp4FrameKey("trkn"),
    FieldKey.URL_BANDCAMP_ARTIST_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_BANDCAMP_ARTIST_SITE"
    ),
    FieldKey.URL_BANDCAMP_RELEASE_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_BANDCAMP_RELEASE_SITE"
    ),
    FieldKey.URL_DISCOGS_ARTIST_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_DISCOGS_ARTIST_SITE"
    ),
    FieldKey.URL_DISCOGS_RELEASE_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_DISCOGS_RELEASE_SITE"
    ),
    FieldKey.URL_LYRICS_SITE to Mp4FrameKey("----", "com.apple.iTunes", "URL_LYRICS_SITE"),
    FieldKey.URL_OFFICIAL_ARTIST_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_OFFICIAL_ARTIST_SITE"
    ),
    FieldKey.URL_OFFICIAL_RELEASE_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_OFFICIAL_RELEASE_SITE"
    ),
    FieldKey.URL_WIKIPEDIA_ARTIST_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_WIKIPEDIA_ARTIST_SITE"
    ),
    FieldKey.URL_WIKIPEDIA_RELEASE_SITE to Mp4FrameKey(
        "----",
        "com.apple.iTunes",
        "URL_WIKIPEDIA_RELEASE_SITE"
    ),
    FieldKey.VERSION to Mp4FrameKey("----", "com.apple.iTunes", "VERSION"),
    FieldKey.WORK to Mp4FrameKey("©wrk"),
    FieldKey.YEAR to Mp4FrameKey("©day"),
    FieldKey.WORK_TYPE to Mp4FrameKey("----", "com.apple.iTunes", "WORK_TYPE"),
)
