package org.jaudiotagger.kt.flac

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.audio.flac.FlacAudioProperties
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.flac.FlacTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlacWriteTest {

    @Test
    fun writeFieldsInPlace() {
        val path = copyToTemp("test.flac", "write-inplace")
        val original = AudioTagger.read(path)
        val originalMd5 = (original.properties as FlacAudioProperties).md5

        val tag = original.tag as FlacTag
        tag.set(FieldKey.ARTIST, "New Artist")
        tag.set(FieldKey.TITLE, "Tïtle 日本語") // non-ASCII round trip
        tag.add(FieldKey.GENRE, "Second Genre")
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path)
        assertEquals("New Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals("Tïtle 日本語", reread.tag.first(FieldKey.TITLE))
        assertEquals(listOf("Crossover", "Second Genre"), reread.tag.all(FieldKey.GENRE))
        // untouched fields survive
        assertEquals("Album", reread.tag.first(FieldKey.ALBUM))
        assertEquals(2, reread.tag.artworks.size)
        // audio itself untouched
        assertEquals(originalMd5, (reread.properties as FlacAudioProperties).md5)

        SystemFileSystem.delete(path)
    }

    @Test
    fun writeGrowingTagShiftsAudio() {
        val path = copyToTemp("test.flac", "write-grow")
        val original = AudioTagger.read(path)
        val originalMd5 = (original.properties as FlacAudioProperties).md5
        val originalSize = SystemFileSystem.metadataOrNull(path)!!.size

        val tag = original.tag as FlacTag
        // Force the metadata not to fit into the existing padding
        tag.set(FieldKey.LYRICS, "x".repeat(200_000))
        AudioTagger.write(path, tag)

        val newSize = SystemFileSystem.metadataOrNull(path)!!.size
        assertTrue(
            newSize > originalSize + 190_000,
            "file should have grown, $originalSize -> $newSize"
        )

        val reread = AudioTagger.read(path)
        assertEquals(200_000, reread.tag.first(FieldKey.LYRICS)!!.length)
        assertEquals("Album", reread.tag.first(FieldKey.ALBUM))
        assertEquals(2, reread.tag.artworks.size)
        assertEquals(originalMd5, (reread.properties as FlacAudioProperties).md5)

        SystemFileSystem.delete(path)
    }

    @Test
    fun writeArtwork() {
        val path = copyToTemp("test.flac", "write-artwork")
        val file = AudioTagger.read(path)

        val tag = file.tag as FlacTag
        val imageBytes = ByteArray(1234) { it.toByte() }
        tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/jpeg", description = "front"))
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path)
        assertEquals(1, reread.tag.artworks.size)
        val artwork = reread.tag.artworks[0]
        assertEquals("image/jpeg", artwork.mimeType)
        assertEquals("front", artwork.description)
        assertTrue(artwork.data.contentEquals(imageBytes))

        SystemFileSystem.delete(path)
    }

    @Test
    fun deleteTag() {
        val path = copyToTemp("test.flac", "delete-tag")
        val originalMd5 = (AudioTagger.read(path).properties as FlacAudioProperties).md5

        AudioTagger.deleteTag(path)

        val reread = AudioTagger.read(path)
        assertEquals(null, reread.tag.first(FieldKey.ARTIST))
        assertEquals(0, reread.tag.artworks.size)
        val properties = assertIs<FlacAudioProperties>(reread.properties)
        assertEquals(originalMd5, properties.md5)

        SystemFileSystem.delete(path)
    }

    @Test
    fun combinedTrackNumberRoundTrip() {
        val path = copyToTemp("test.flac", "flac-track-total")
        val tag = AudioTagger.read(path).tag as FlacTag
        tag.vorbisComment.setRaw("TRACKNUMBER", "3/12")
        AudioTagger.write(path, tag)

        val withTotal = AudioTagger.read(path)
        assertEquals("3", withTotal.tag.first(FieldKey.TRACK))
        assertEquals("12", withTotal.tag.first(FieldKey.TRACK_TOTAL))

        withTotal.tag.remove(FieldKey.TRACK)
        AudioTagger.write(path, withTotal.tag)
        val afterRemove = AudioTagger.read(path)
        assertEquals(null, afterRemove.tag.first(FieldKey.TRACK))
        assertEquals("12", afterRemove.tag.first(FieldKey.TRACK_TOTAL))

        SystemFileSystem.delete(path)
    }
}
