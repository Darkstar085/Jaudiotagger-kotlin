package org.jaudiotagger.kt.dsf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.testDataPath

class DsfTest {

    @Test
    fun readProperties() {
        val file = AudioTagger.read(testDataPath("test122.dsf"))
        assertEquals("DSF", file.properties.format)
        // DSD64: 2.8224 MHz, 1 bit per sample
        assertEquals(2822400, file.properties.sampleRate)
        assertEquals(1, file.properties.bitsPerSample)
        assertEquals(2, file.properties.channels)
        assertTrue(file.properties.isLossless)
        assertTrue(file.properties.duration.inWholeSeconds > 0)
    }

    @Test
    fun readExistingTag() {
        val tag = AudioTagger.read(testDataPath("test122.dsf")).tag
        assertIs<Id3v2Tag>(tag)
    }

    @Test
    fun writeTagRoundTrip() {
        val path = copyToTemp("test122.dsf", "dsf-write")
        val originalDuration = AudioTagger.read(path).properties.duration

        val tag = AudioTagger.read(path).tag as Id3v2Tag
        tag.set(FieldKey.TITLE, "DSF Tïtle")
        tag.set(FieldKey.ARTIST, "DSF Artist")
        val imageBytes = ByteArray(2222) { it.toByte() }
        tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/png"))
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path)
        assertEquals("DSF Tïtle", reread.tag.first(FieldKey.TITLE))
        assertEquals("DSF Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals(1, reread.tag.artworks.size)
        assertTrue(reread.tag.artworks[0].data.contentEquals(imageBytes))
        assertEquals(originalDuration, reread.properties.duration)

        AudioTagger.deleteTag(path)
        val deleted = AudioTagger.read(path)
        assertEquals(null, deleted.tag.first(FieldKey.TITLE))
        assertEquals(originalDuration, deleted.properties.duration)

        SystemFileSystem.delete(path)
    }
}
