package org.jaudiotagger.kt.mp4

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.mp4.Mp4Tag
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Expected values match the original M4aReadTagTest for testdata/test.m4a.
 */
class Mp4ReadTest {

    @Test
    fun readFile() {
        val file = AudioTagger.read(testDataPath("test.m4a"))

        // the original rounds to 242; the precise duration is ~241.9s
        assertEquals(242, ((file.properties.duration.inWholeMilliseconds + 500) / 1000).toInt())
        assertEquals(44100, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(128, file.properties.bitRate)
        assertEquals("AAC", file.properties.encodingType)

        val tag = assertIs<Mp4Tag>(file.tag)
        assertEquals("Artist", tag.first(FieldKey.ARTIST))
        assertEquals("Album", tag.first(FieldKey.ALBUM))
        assertEquals("title", tag.first(FieldKey.TITLE))
    }

    @Test
    fun readSamplesSmoke() {
        for (sample in listOf(
            "test.m4a",
            "test2.m4a",
            "test3.m4a",
            "test4.m4a",
            "test5.m4a",
            "test8.m4a",
            "test164.m4a"
        )) {
            if (SystemFileSystem.metadataOrNull(testDataPath(sample)) == null) continue
            val file = AudioTagger.read(testDataPath(sample))
            assertTrue(file.properties.sampleRate > 0, sample)
            assertTrue(file.properties.duration.inWholeMilliseconds > 0, sample)
            assertIs<Mp4Tag>(file.tag, sample)
        }
    }
}
