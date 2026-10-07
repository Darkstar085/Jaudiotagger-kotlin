package org.jaudiotagger.kt.mp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version
import org.jaudiotagger.kt.testDataPath

class Id3v2ReadTest {

    @Test
    fun readV23Tag() {
        val tag = assertIs<Id3v2Tag>(AudioTagger.read(testDataPath("testV1Cbr128ID3v2.mp3")).tag)
        assertEquals(Id3v2Version.V23, tag.version)
        assertEquals("testtitle", tag.first(FieldKey.TITLE))
        assertEquals("testartist", tag.first(FieldKey.ARTIST))
        assertEquals("testalbum", tag.first(FieldKey.ALBUM))
        assertEquals("1999", tag.first(FieldKey.YEAR))
        assertEquals("100", tag.first(FieldKey.TRACK))
        // TCON holds "(1)": resolves through the ID3v1 genre table
        assertEquals("Classic Rock", tag.first(FieldKey.GENRE))
        assertEquals("testcomment", tag.first(FieldKey.COMMENT))
    }

    @Test
    fun readV23TagWithLargePadding() {
        val tag = assertIs<Id3v2Tag>(AudioTagger.read(testDataPath("issue52.mp3")).tag)
        assertEquals("The Dead Can't Testify", tag.first(FieldKey.TITLE))
        assertEquals("Billy Talent", tag.first(FieldKey.ARTIST))
        assertEquals("Billy Talent", tag.first(FieldKey.ALBUM_ARTIST))
        assertEquals("Rock", tag.first(FieldKey.GENRE))
    }

    @Test
    fun readV24Tag() {
        val tag = assertIs<Id3v2Tag>(AudioTagger.read(testDataPath("test23.mp3")).tag)
        assertEquals(Id3v2Version.V24, tag.version)
        assertEquals("Kogani", tag.first(FieldKey.TITLE))
        assertEquals("Suerte", tag.first(FieldKey.ARTIST))
        assertEquals("Kogani", tag.first(FieldKey.ALBUM))
        // v2.4 stores the year in TDRC
        assertEquals("-1", tag.first(FieldKey.YEAR))
        assertTrue(tag.first(FieldKey.LYRICS)!!.startsWith("When reading lyrics from a mp3"))
    }

    @Test
    fun readV22Tag() {
        val tag = assertIs<Id3v2Tag>(AudioTagger.read(testDataPath("test51.mp3")).tag)
        assertEquals(Id3v2Version.V22, tag.version)
        assertEquals("If You're Happy and You Know I", tag.first(FieldKey.TITLE))
        assertEquals("Barney", tag.first(FieldKey.ARTIST))
        // UTF-16 value with a typographic apostrophe
        assertEquals("Children’s Music", tag.first(FieldKey.GENRE))
    }

    @Test
    fun inMemoryModification() {
        val tag = assertIs<Id3v2Tag>(AudioTagger.read(testDataPath("testV1Cbr128ID3v2.mp3")).tag)
        tag.set(FieldKey.TITLE, "replaced")
        assertEquals("replaced", tag.first(FieldKey.TITLE))
        tag.add(FieldKey.GENRE, "Second Genre")
        assertEquals(listOf("Classic Rock", "Second Genre"), tag.all(FieldKey.GENRE))
        tag.remove(FieldKey.COMMENT)
        assertEquals(null, tag.first(FieldKey.COMMENT))
        tag.set(FieldKey.MUSICBRAINZ_RELEASEID, "some-uuid")
        assertEquals("some-uuid", tag.first(FieldKey.MUSICBRAINZ_RELEASEID))
    }
}
