package org.jaudiotagger.kt.ogg

import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OggReadTest {

    @Test
    fun readFile() {
        val file = AudioTagger.read(testDataPath("test.ogg"))

        assertEquals("Ogg Vorbis v1", file.properties.encodingType)
        assertEquals(44100, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(192, file.properties.bitRate)
        assertTrue(file.properties.isVariableBitRate)
        assertEquals(58752, file.properties.totalSamples)

        val tag = assertIs<VorbisCommentTag>(file.tag)
        assertEquals("jaudiotagger", tag.vendor)
        assertEquals("artist", tag.first(FieldKey.ARTIST))
        assertEquals("ddd", tag.first(FieldKey.ALBUM))
    }

    @Test
    fun readAllOggPages() {
        // same invariants the original OggPageTest checks: 10 valid contiguous pages
        assertEquals(10, validateOggPageStructure(testDataPath("test.ogg")))
    }
}
