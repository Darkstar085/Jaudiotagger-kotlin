package org.jaudiotagger.kt.mp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.audio.mp3.Mp3AudioProperties
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v1Tag
import org.jaudiotagger.kt.testDataPath

/**
 * Expected values match the original MP3AudioHeaderTest.
 */
class Mp3Test {

    @Test
    fun readVbrWithXingHeader() {
        for (sample in listOf("testV1vbrOld0.mp3", "testV1vbrNew0.mp3")) {
            val p = assertIs<Mp3AudioProperties>(AudioTagger.read(testDataPath(sample)).properties)
            assertEquals(44100, p.sampleRate, sample)
            assertEquals("MPEG-1 Layer 3", p.encodingType, sample)
            assertEquals("Mono", p.channelMode, sample)
            assertTrue(p.isVariableBitRate, sample)
            assertEquals(127, p.bitRate, sample)
            assertEquals(14, p.duration.inWholeSeconds.toInt(), sample)
            assertEquals("LAME3.96r", p.encoder, sample)
        }
    }

    @Test
    fun readCbr() {
        val p = assertIs<Mp3AudioProperties>(AudioTagger.read(testDataPath("testV1Cbr128.mp3")).properties)
        assertEquals(44100, p.sampleRate)
        assertFalse(p.isVariableBitRate)
        assertEquals(128, p.bitRate)
        assertEquals(14, p.duration.inWholeSeconds.toInt())
    }

    @Test
    fun readLayer2() {
        val mono = assertIs<Mp3AudioProperties>(AudioTagger.read(testDataPath("testV1L2mono.mp3")).properties)
        assertEquals("MPEG-1 Layer 2", mono.encodingType)
        assertEquals(1, mono.channels)
        assertEquals(192, mono.bitRate)

        val stereo = assertIs<Mp3AudioProperties>(AudioTagger.read(testDataPath("testV1L2stereo.mp3")).properties)
        assertEquals(2, stereo.channels)
    }

    @Test
    fun skipsLargeId3v2TagBeforeAudio() {
        val file = AudioTagger.read(testDataPath("issue52.mp3"))
        val p = assertIs<Mp3AudioProperties>(file.properties)
        assertEquals(261224, p.audioDataStartPosition)
        assertEquals("Stereo", p.channelMode)
        assertEquals(256, p.bitRate)
        // trailing ID3v1 is read even when an (unported) ID3v2 tag is present
        assertEquals("Billy Talent", file.tag.first(FieldKey.ARTIST))
        assertEquals("7", file.tag.first(FieldKey.TRACK))
    }

    @Test
    fun readId3v1Tag() {
        val tag = AudioTagger.read(testDataPath("testV1Cbr128ID3v1.mp3")).tag
        assertEquals("testtitle", tag.first(FieldKey.TITLE))
        assertEquals("testartist", tag.first(FieldKey.ARTIST))
        assertEquals("testalbum", tag.first(FieldKey.ALBUM))
        assertEquals("1999", tag.first(FieldKey.YEAR))
        assertEquals("testcomment", tag.first(FieldKey.COMMENT))
        assertEquals("Classic Rock", tag.first(FieldKey.GENRE))
        assertEquals("100", tag.first(FieldKey.TRACK))
    }

    @Test
    fun writeId3v1RoundTrip() {
        val path = copyToTemp("testV1Cbr128.mp3", "mp3-write")
        val originalSize = SystemFileSystem.metadataOrNull(path)!!.size

        val tag = Id3v1Tag()
        tag.set(FieldKey.TITLE, "New Title")
        tag.set(FieldKey.ARTIST, "New Artist")
        tag.set(FieldKey.GENRE, "Rock")
        tag.set(FieldKey.TRACK, "12")
        AudioTagger.write(path, tag)

        // appended exactly one 128-byte block
        assertEquals(originalSize + 128, SystemFileSystem.metadataOrNull(path)!!.size)

        val reread = AudioTagger.read(path)
        assertEquals("New Title", reread.tag.first(FieldKey.TITLE))
        assertEquals("New Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals("Rock", reread.tag.first(FieldKey.GENRE))
        assertEquals("12", reread.tag.first(FieldKey.TRACK))

        // rewriting replaces the existing block instead of appending another
        val tag2 = reread.tag as Id3v1Tag
        tag2.set(FieldKey.TITLE, "Third Title")
        AudioTagger.write(path, tag2)
        assertEquals(originalSize + 128, SystemFileSystem.metadataOrNull(path)!!.size)
        assertEquals("Third Title", AudioTagger.read(path).tag.first(FieldKey.TITLE))

        // delete restores the original file exactly
        AudioTagger.deleteTag(path)
        assertEquals(originalSize, SystemFileSystem.metadataOrNull(path)!!.size)
        assertEquals(null, AudioTagger.read(path).tag.first(FieldKey.TITLE))

        SystemFileSystem.delete(path)
    }

    @Test
    fun corruptFileFailsWithCannotRead() {
        assertFailsWith<CannotReadException> {
            AudioTagger.read(testDataPath("corrupt.mp3"))
        }
    }
}
