package org.jaudiotagger.kt.wav

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.wav.WavTag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Expected values match the original WavSimpleTest.
 */
class WavTest {

    @Test
    fun readSimpleFile() {
        val file = AudioTagger.read(testDataPath("test.wav"))
        assertEquals(176, file.properties.bitRate)
        assertEquals("WAV PCM 8 bits", file.properties.encodingType)
        assertEquals(1, file.properties.channels)
        assertEquals(22050, file.properties.sampleRate)
        assertEquals(8, file.properties.bitsPerSample)
        assertEquals(14, file.properties.duration.inWholeSeconds.toInt())
        assertTrue(file.properties.isLossless)
        assertIs<WavTag>(file.tag)
    }

    @Test
    fun readSamplesSmoke() {
        for (sample in listOf("test123.wav", "test125.wav", "test126.wav", "bug153.wav")) {
            if (SystemFileSystem.metadataOrNull(testDataPath(sample)) == null) continue
            val file = AudioTagger.read(testDataPath(sample))
            assertTrue(file.properties.sampleRate > 0, sample)
            assertIs<WavTag>(file.tag, sample)
        }
    }

    @Test
    fun writeTagRoundTrip() {
        val path = copyToTemp("test.wav", "wav-write")
        val originalDuration = AudioTagger.read(path).properties.duration

        val tag = AudioTagger.read(path).tag as WavTag
        tag.set(FieldKey.TITLE, "Wav Tïtle")
        tag.set(FieldKey.ARTIST, "Wav Artist")
        tag.set(FieldKey.YEAR, "2020")
        val imageBytes = ByteArray(3000) { it.toByte() }
        tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/png"))
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path)
        val rereadTag = assertIs<WavTag>(reread.tag)
        assertEquals("Wav Tïtle", rereadTag.first(FieldKey.TITLE))
        assertEquals("Wav Artist", rereadTag.first(FieldKey.ARTIST))
        assertEquals("2020", rereadTag.first(FieldKey.YEAR))
        // mirrored into the legacy INFO chunk as well
        assertEquals("Wav Artist", rereadTag.infoFields["IART"])
        assertEquals(1, rereadTag.artworks.size)
        assertTrue(rereadTag.artworks[0].data.contentEquals(imageBytes))
        assertEquals(originalDuration, reread.properties.duration)

        // rewrite replaces the chunks, not appends
        val sizeAfterFirstWrite = SystemFileSystem.metadataOrNull(path)!!.size
        AudioTagger.write(path, rereadTag)
        assertEquals(sizeAfterFirstWrite, SystemFileSystem.metadataOrNull(path)!!.size)

        AudioTagger.deleteTag(path)
        val deleted = AudioTagger.read(path)
        assertEquals(null, deleted.tag.first(FieldKey.TITLE))
        assertEquals(originalDuration, deleted.properties.duration)
        assertEquals(
            SystemFileSystem.metadataOrNull(testDataPath("test.wav"))!!.size,
            SystemFileSystem.metadataOrNull(path)!!.size,
        )

        SystemFileSystem.delete(path)
    }

    @Test
    fun existingMetadataSurvivesRewrite() {
        // test123.wav carries LIST-INFO metadata in the wild
        val source = testDataPath("test123.wav")
        if (SystemFileSystem.metadataOrNull(source) == null) return
        val path = copyToTemp("test123.wav", "wav-existing")

        val before = AudioTagger.read(path).tag as WavTag
        val artistBefore = before.first(FieldKey.ARTIST)
        before.set(FieldKey.TITLE, "Replaced Title")
        AudioTagger.write(path, before)

        val after = AudioTagger.read(path).tag as WavTag
        assertEquals("Replaced Title", after.first(FieldKey.TITLE))
        assertEquals(artistBefore, after.first(FieldKey.ARTIST))

        SystemFileSystem.delete(path)
    }
}
