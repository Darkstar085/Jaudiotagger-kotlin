package org.jaudiotagger.kt.aiff

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

class AiffTest {

    @Test
    fun readSamplesSmoke() {
        for (sample in listOf("test119.aif", "test120.aif", "test121.aif", "test124.aif")) {
            if (SystemFileSystem.metadataOrNull(testDataPath(sample)) == null) continue
            val file = AudioTagger.read(testDataPath(sample))
            assertTrue(file.properties.sampleRate > 0, sample)
            assertTrue(file.properties.channels > 0, sample)
            assertTrue(file.properties.duration.inWholeMilliseconds > 0, sample)
        }
    }

    @Test
    fun writeTagRoundTrip() {
        val path = copyToTemp("test119.aif", "aiff-write")
        val originalDuration = AudioTagger.read(path).properties.duration

        val tag = AudioTagger.read(path).tag as Id3v2Tag
        tag.set(FieldKey.TITLE, "Aiff Tïtle")
        tag.set(FieldKey.ARTIST, "Aiff Artist")
        val imageBytes = ByteArray(1500) { it.toByte() }
        tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/jpeg"))
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path)
        val rereadTag = assertIs<Id3v2Tag>(reread.tag)
        assertEquals("Aiff Tïtle", rereadTag.first(FieldKey.TITLE))
        assertEquals("Aiff Artist", rereadTag.first(FieldKey.ARTIST))
        assertEquals(1, rereadTag.artworks.size)
        assertTrue(rereadTag.artworks[0].data.contentEquals(imageBytes))
        assertEquals(originalDuration, reread.properties.duration)

        AudioTagger.deleteTag(path)
        val deleted = AudioTagger.read(path)
        assertEquals(null, deleted.tag.first(FieldKey.TITLE))
        assertEquals(originalDuration, deleted.properties.duration)

        SystemFileSystem.delete(path)
    }
}
