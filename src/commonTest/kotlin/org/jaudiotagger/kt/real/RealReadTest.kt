package org.jaudiotagger.kt.real

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.testDataPath

class RealReadTest {

    @Test
    fun readRaFile() {
        val file = AudioTagger.read(testDataPath("test01.ra"))
        assertEquals("RA", file.properties.format)
        assertEquals(16, file.properties.bitRate)
        assertEquals(174, file.properties.duration.inWholeSeconds.toInt())
        assertEquals("Temptation Rag", file.tag.first(FieldKey.TITLE))
        assertEquals("Prince's Military Band", file.tag.first(FieldKey.ARTIST))
        assertEquals("1910 [Columbia A854]", file.tag.first(FieldKey.COMMENT))
    }

    @Test
    fun readRmFile() {
        val file = AudioTagger.read(testDataPath("test05.rm"))
        assertEquals(32, file.properties.bitRate)
        assertEquals("It Makes My Love Come Down", file.tag.first(FieldKey.TITLE))
    }

    @Test
    fun writeIsRejected() {
        val file = AudioTagger.read(testDataPath("test01.ra"))
        assertFailsWith<CannotWriteException> {
            AudioTagger.write(testDataPath("test01.ra"), file.tag)
        }
    }
}
