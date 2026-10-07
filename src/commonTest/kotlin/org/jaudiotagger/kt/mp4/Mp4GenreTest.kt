package org.jaudiotagger.kt.mp4

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.GenreTypes
import org.jaudiotagger.kt.tag.mp4.Mp4Item
import org.jaudiotagger.kt.tag.mp4.Mp4Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Mp4GenreTest {

    @Test
    fun gnreAtomSurvivesReadWriteRoundTrip() {
        val path = copyToTemp("test.m4a", "mp4-gnre-roundtrip")
        val tag = AudioTagger.read(path).tag as Mp4Tag
        tag.set(FieldKey.GENRE, "Rock")
        AudioTagger.write(path, tag)

        val edited = AudioTagger.read(path).tag as Mp4Tag
        edited.set(FieldKey.TITLE, "Edited title")
        AudioTagger.write(path, edited)

        val reread = AudioTagger.read(path).tag as Mp4Tag
        assertTrue(
            reread.items.any { it is Mp4Item.Genre },
            "gnre must stay a gnre atom after rewrite"
        )
        assertEquals("Rock", reread.first(FieldKey.GENRE))

        SystemFileSystem.delete(path)
    }

    @Test
    fun setStandardGenreWritesGnreAtom() {
        val path = copyToTemp("test.m4a", "mp4-gnre-set")
        val tag = AudioTagger.read(path).tag as Mp4Tag
        tag.set(FieldKey.GENRE, "Rock")
        AudioTagger.write(path, tag)

        val reread = assertIs<Mp4Tag>(AudioTagger.read(path).tag)
        assertEquals("Rock", reread.first(FieldKey.GENRE))
        val expectedGnreId = GenreTypes.idOf("Rock")!! + 1
        assertTrue(reread.items.any { it is Mp4Item.Genre && it.genreId == expectedGnreId })

        SystemFileSystem.delete(path)
    }

    @Test
    fun setCustomGenreWritesCustomTextAtom() {
        val path = copyToTemp("test.m4a", "mp4-genre-custom")
        val tag = AudioTagger.read(path).tag as Mp4Tag
        tag.set(FieldKey.GENRE, "Rock, Hardrock")
        AudioTagger.write(path, tag)

        val reread = assertIs<Mp4Tag>(AudioTagger.read(path).tag)
        assertEquals("Rock, Hardrock", reread.first(FieldKey.GENRE))
        assertTrue(reread.items.any { it is Mp4Item.Text && it.atomId == "\u00A9gen" })

        SystemFileSystem.delete(path)
    }

    @Test
    fun readCustomGenreFromExistingFile() {
        val path = copyToTemp("test.m4a", "mp4-genre-read")
        val tag = AudioTagger.read(path).tag as Mp4Tag
        assertEquals("Genre", tag.first(FieldKey.GENRE))
        SystemFileSystem.delete(path)
    }

    @Test
    fun removeTrackKeepsTotal() {
        val path = copyToTemp("test.m4a", "mp4-trkn-remove")
        val tag = AudioTagger.read(path).tag as Mp4Tag
        tag.set(FieldKey.TRACK, "3")
        tag.set(FieldKey.TRACK_TOTAL, "12")
        tag.remove(FieldKey.TRACK)
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path).tag as Mp4Tag
        assertEquals(null, reread.first(FieldKey.TRACK))
        assertEquals("12", reread.first(FieldKey.TRACK_TOTAL))
        assertTrue(reread.items.any { it is Mp4Item.NumberPair && it.atomId == "trkn" })

        SystemFileSystem.delete(path)
    }

    @Test
    fun removeGenreClearsBothAtoms() {
        val path = copyToTemp("test.m4a", "mp4-genre-remove")
        val tag = AudioTagger.read(path).tag as Mp4Tag
        tag.set(FieldKey.GENRE, "Rock")
        tag.remove(FieldKey.GENRE)
        AudioTagger.write(path, tag)

        val reread = assertIs<Mp4Tag>(AudioTagger.read(path).tag)
        assertEquals(null, reread.first(FieldKey.GENRE))
        assertTrue(reread.items.none { it is Mp4Item.Genre || (it is Mp4Item.Text && it.atomId == "\u00A9gen") })

        SystemFileSystem.delete(path)
    }
}
