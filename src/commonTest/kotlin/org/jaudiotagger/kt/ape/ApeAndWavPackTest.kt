package org.jaudiotagger.kt.ape

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.ape.ApeTag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * test0001.ape/test0001.wv and test0002.ape/test0002.wv are the same two
 * recordings encoded twice, so sample counts must agree across formats.
 */
class ApeAndWavPackTest {

    @Test
    fun readMonkeyAudioProperties() {
        val file = AudioTagger.read(testDataPath("test0002.ape"))
        assertEquals("Monkey's Audio", file.properties.format)
        assertEquals(48000, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(16, file.properties.bitsPerSample)
        assertEquals(10776960, file.properties.totalSamples)
        assertTrue(file.properties.isLossless)
    }

    @Test
    fun readWavPackProperties() {
        val file = AudioTagger.read(testDataPath("test0001.wv"))
        assertEquals("WavPack", file.properties.format)
        assertEquals(44100, file.properties.sampleRate)
        assertEquals(1, file.properties.channels)
        assertEquals(620880, file.properties.totalSamples)
        assertTrue(file.properties.isLossless)
    }

    @Test
    fun monkeyAndWavPackEncodingsOfSameAudioAgree() {
        val ape = AudioTagger.read(testDataPath("test0001.ape")).properties
        val wv = AudioTagger.read(testDataPath("test0001.wv")).properties
        assertEquals(ape.totalSamples, wv.totalSamples)
        assertEquals(ape.sampleRate, wv.sampleRate)
        assertEquals(ape.channels, wv.channels)
    }

    @Test
    fun writeApeTagRoundTrip() {
        for (sample in listOf("test0001.ape", "test0001.wv")) {
            val path = copyToTemp(sample, "ape-write")
            val originalSamples = AudioTagger.read(path).properties.totalSamples

            val tag = AudioTagger.read(path).tag as ApeTag
            tag.set(FieldKey.ARTIST, "Ape Artist")
            tag.set(FieldKey.TITLE, "Tïtle 日本語")
            tag.set(FieldKey.TRACK, "7")
            val imageBytes = ByteArray(999) { it.toByte() }
            tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/jpeg"))
            AudioTagger.write(path, tag)

            val reread = AudioTagger.read(path)
            assertEquals("Ape Artist", reread.tag.first(FieldKey.ARTIST), sample)
            assertEquals("Tïtle 日本語", reread.tag.first(FieldKey.TITLE), sample)
            assertEquals("7", reread.tag.first(FieldKey.TRACK), sample)
            assertEquals(1, reread.tag.artworks.size, sample)
            assertTrue(reread.tag.artworks[0].data.contentEquals(imageBytes), sample)
            // audio untouched
            assertEquals(originalSamples, reread.properties.totalSamples, sample)

            // rewrite with fewer fields: tag shrinks, file stays consistent
            val tag2 = reread.tag as ApeTag
            tag2.remove(FieldKey.TITLE)
            tag2.clearArtworks()
            AudioTagger.write(path, tag2)
            val reread2 = AudioTagger.read(path)
            assertEquals("Ape Artist", reread2.tag.first(FieldKey.ARTIST), sample)
            assertEquals(null, reread2.tag.first(FieldKey.TITLE), sample)
            assertEquals(0, reread2.tag.artworks.size, sample)
            assertEquals(originalSamples, reread2.properties.totalSamples, sample)

            // delete removes the tag entirely and restores the original file size
            AudioTagger.deleteTag(path)
            val reread3 = AudioTagger.read(path)
            assertEquals(null, reread3.tag.first(FieldKey.ARTIST), sample)
            assertEquals(originalSamples, reread3.properties.totalSamples, sample)
            assertEquals(
                SystemFileSystem.metadataOrNull(testDataPath(sample))!!.size,
                SystemFileSystem.metadataOrNull(path)!!.size,
                sample,
            )

            SystemFileSystem.delete(path)
        }
    }
}
