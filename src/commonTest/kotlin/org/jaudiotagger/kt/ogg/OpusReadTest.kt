package org.jaudiotagger.kt.ogg

import org.jaudiotagger.kt.AudioFile
import org.jaudiotagger.kt.AudioFormat
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

private const val FFMPEG_DECODE_COUNT = 144000L // ffmpeg decode count

class OpusReadTest {

    @Test
    fun readsTagsAndProperties() {
        val file = AudioTagger.read(testDataPath("test-opus.opus"))
        assertOpusFixture(file)
        assertEquals(AudioFormat.OGG, file.format)
    }

    @Test
    fun readsOpusStreamWithOggExtension() {
        assertOpusFixture(AudioTagger.read(testDataPath("test-opus-in-ogg.ogg")))
    }

    @Test
    fun paddingAndBinaryTailDoNotAffectReading() {
        assertOpusFixture(AudioTagger.read(testDataPath("test-opus-padding.opus")))
        assertOpusFixture(AudioTagger.read(testDataPath("test-opus-binary-tail.opus")))
    }

    @Test
    fun commentSharingLastPageWithAudioStillReads() {
        val file = AudioTagger.read(testDataPath("test-opus-shared-page.opus"))
        assertOpusTags(file.tag as VorbisCommentTag)
        assertEquals(FFMPEG_DECODE_COUNT, file.properties.totalSamples)
    }

    @Test
    fun readsCombinedTrackAndDiscNumbers() {
        val file = AudioTagger.read(testDataPath("test-opus-track-total.opus"))
        val tag = assertIs<VorbisCommentTag>(file.tag)
        assertEquals("3", tag.first(FieldKey.TRACK))
        assertEquals("12", tag.first(FieldKey.TRACK_TOTAL))
        assertEquals("1", tag.first(FieldKey.DISC_NO))
        assertEquals("2", tag.first(FieldKey.DISC_TOTAL))
    }

    @Test
    fun oggVorbisStillReadsAsVorbis() {
        val file = AudioTagger.read(testDataPath("test.ogg"))
        assertEquals("Ogg Vorbis v1", file.properties.encodingType)
        assertEquals(44100, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(192, file.properties.bitRate)
        assertEquals(58752, file.properties.totalSamples)
    }

    private fun assertOpusFixture(file: AudioFile) {
        val tag = assertIs<VorbisCommentTag>(file.tag)
        assertOpusTags(tag)
        assertEquals(48_000, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(FFMPEG_DECODE_COUNT, file.properties.totalSamples)
        assertEquals((FFMPEG_DECODE_COUNT.toDouble() / 48_000).seconds, file.properties.duration)
        assertEquals("Ogg Opus", file.properties.encodingType)
    }

    private fun assertOpusTags(tag: VorbisCommentTag) {
        assertEquals("Opus Title", tag.first(FieldKey.TITLE))
        assertEquals("Opus Artist", tag.first(FieldKey.ARTIST))
        assertEquals("Opus Album", tag.first(FieldKey.ALBUM))
        assertEquals("Rock", tag.first(FieldKey.GENRE))
        assertEquals("3", tag.first(FieldKey.TRACK))
        assertEquals("12", tag.first(FieldKey.TRACK_TOTAL))
    }
}
