package org.jaudiotagger.kt.flac

import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.audio.flac.FlacAudioProperties
import org.jaudiotagger.kt.audio.flac.FlacInfoReader
import org.jaudiotagger.kt.io.withFileIo
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.flac.FlacTag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Expected values match the original jaudiotagger FlacHeaderTest for testdata/test.flac.
 */
class FlacReadTest {

    @Test
    fun readFileWithVorbisComment() {
        val file = AudioTagger.read(testDataPath("test.flac"))

        val properties = assertIs<FlacAudioProperties>(file.properties)
        assertEquals(192, properties.bitRate)
        assertEquals("FLAC 16 bits", properties.encodingType)
        assertEquals(2, properties.channels)
        assertEquals(44100, properties.sampleRate)
        assertEquals(5, properties.duration.inWholeSeconds.toInt())
        assertTrue(properties.isLossless)

        val tag = assertIs<FlacTag>(file.tag)
        assertEquals("Artist", tag.first(FieldKey.ARTIST))
        assertEquals("Album", tag.first(FieldKey.ALBUM))
        assertEquals("test3", tag.first(FieldKey.TITLE))
        assertEquals("comments", tag.first(FieldKey.COMMENT))
        assertEquals("1971", tag.first(FieldKey.YEAR))
        assertEquals("4", tag.first(FieldKey.TRACK))
        assertEquals("Crossover", tag.first(FieldKey.GENRE))
        assertEquals("Composer", tag.first(FieldKey.COMPOSER))

        assertEquals(2, tag.artworks.size)

        val image = tag.artworks[0]
        assertEquals(3, image.pictureType)
        assertEquals("image/png", image.mimeType)
        assertFalse(image.isLinked)
        assertEquals("", image.description)
        assertEquals(0, image.width)
        assertEquals(0, image.height)
        assertEquals(0, image.colourDepth)
        assertEquals(0, image.indexedColourCount)
        assertEquals(18545, image.data.size)

        val linkedImage = tag.artworks[1]
        assertTrue(linkedImage.isLinked)
        assertTrue(linkedImage.linkedUrl.isNotEmpty())
    }

    @Test
    fun countMetaBlocks() {
        withFileIo(testDataPath("test.flac")) { io ->
            assertEquals(6, FlacInfoReader.countMetaBlocks(io))
        }
    }
}
