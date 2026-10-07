package org.jaudiotagger.kt.dsf

import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.testDataPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DffTest {

    @Test
    fun readProperties() {
        val file = AudioTagger.read(testDataPath("test229.dff"))
        assertEquals("DFF", file.properties.format)
        assertEquals(2822400, file.properties.sampleRate)
        assertEquals(2, file.properties.channels)
        assertEquals(1, file.properties.bitsPerSample)
        assertTrue(file.properties.isLossless)
        assertTrue(file.properties.duration.inWholeSeconds > 0)
    }
}
