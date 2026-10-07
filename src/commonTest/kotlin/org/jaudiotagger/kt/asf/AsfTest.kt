package org.jaudiotagger.kt.asf

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.asf.AsfTag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AsfTest {

    @Test
    fun readFile() {
        val file = AudioTagger.read(testDataPath("test1.wma"))
        assertEquals(32000, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(32, file.properties.bitRate)
        assertEquals(219, file.properties.duration.inWholeSeconds.toInt())

        val tag = assertIs<AsfTag>(file.tag)
        assertEquals("tracktitle", tag.first(FieldKey.TITLE))
        assertEquals("artist", tag.first(FieldKey.ARTIST))
        assertEquals("album", tag.first(FieldKey.ALBUM))
        assertEquals("genre", tag.first(FieldKey.GENRE))
        assertEquals("3", tag.first(FieldKey.TRACK))
        assertEquals("1971", tag.first(FieldKey.YEAR))
        assertEquals(1, tag.artworks.size)
    }

    @Test
    fun readSamplesSmoke() {
        for (sample in listOf("test2.wma", "test3.wma", "test5.wma", "test7.wma", "test509.wma")) {
            if (SystemFileSystem.metadataOrNull(testDataPath(sample)) == null) continue
            val file = AudioTagger.read(testDataPath(sample))
            assertTrue(file.properties.sampleRate > 0, sample)
            assertTrue(file.properties.duration.inWholeSeconds > 0, sample)
            assertIs<AsfTag>(file.tag, sample)
        }
    }

    @Test
    fun writeTagRoundTrip() {
        val path = copyToTemp("test1.wma", "asf-write")
        val originalDuration = AudioTagger.read(path).properties.duration

        val tag = AudioTagger.read(path).tag as AsfTag
        tag.set(FieldKey.TITLE, "Wma Tïtle")
        tag.set(FieldKey.ARTIST, "Wma Artist")
        tag.set(FieldKey.MUSICBRAINZ_RELEASEID, "wma-uuid")
        val imageBytes = ByteArray(5000) { (it * 3).toByte() }
        tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/png", description = "front"))
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path)
        val rereadTag = assertIs<AsfTag>(reread.tag)
        assertEquals("Wma Tïtle", rereadTag.first(FieldKey.TITLE))
        assertEquals("Wma Artist", rereadTag.first(FieldKey.ARTIST))
        assertEquals("wma-uuid", rereadTag.first(FieldKey.MUSICBRAINZ_RELEASEID))
        assertEquals("album", rereadTag.first(FieldKey.ALBUM), "untouched field must survive")
        assertEquals(1, rereadTag.artworks.size)
        assertEquals("front", rereadTag.artworks[0].description)
        assertTrue(rereadTag.artworks[0].data.contentEquals(imageBytes))
        assertEquals(originalDuration, reread.properties.duration)

        AudioTagger.deleteTag(path)
        val deleted = AudioTagger.read(path)
        assertEquals(null, deleted.tag.first(FieldKey.TITLE))
        assertEquals(originalDuration, deleted.properties.duration)

        SystemFileSystem.delete(path)
    }

    @Test
    fun readKeepsDuplicateDescriptorNamesInOrder() {
        val tag = assertIs<AsfTag>(AudioTagger.read(testDataPath("test1.wma")).tag)
        // IsVBR exists in both Metadata and Extended Content; ECD value wins for first().
        assertEquals(2, tag.allRaw("IsVBR").size)
    }
}
