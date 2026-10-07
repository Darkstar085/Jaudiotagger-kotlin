package org.jaudiotagger.kt.mp3

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.audio.mp3.Mp3AudioProperties
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.io.openFileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.testDataPath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Mp3DurationTest {

    @Test
    fun test74DurationMatchesFfprobeGroundTruth() {
        assertDurationClose(47.9, durationSeconds(readProps("test74.mp3")), 0.5)
    }

    @Test
    fun test23DurationMatchesFfprobeGroundTruth() {
        assertDurationClose(31.3, durationSeconds(readProps("test23.mp3")), 0.5)
    }

    @Test
    fun testV2vbrOld0DurationMatchesFfprobeGroundTruth() {
        assertDurationClose(14.0, durationSeconds(readProps("testV2vbrOld0.mp3")), 0.5)
    }

    @Test
    fun testV25vbrOld0DurationMatchesFfprobeGroundTruth() {
        assertDurationClose(14.0, durationSeconds(readProps("testV25vbrOld0.mp3")), 0.5)
    }

    @Test
    fun testV2L3StereoDurationIsOneSecondNotDoubled() {
        assertEquals(1, readProps("testV2L3Stereo.mp3").duration.inWholeSeconds)
    }

    @Test
    fun mpeg2Layer3XingDurationUses576SamplesPerFrame() {
        val props = readProps("testV2vbrOld0.mp3")
        assertTrue(props.isVariableBitRate)
        assertTrue(props.numberOfFrames > 0)
        val expectedSeconds = props.numberOfFrames * 576.0 / props.sampleRate
        assertDurationClose(expectedSeconds, props.duration.inWholeSeconds.toDouble(), 1.0)
    }

    @Test
    fun mpeg25Layer3VbrUsesXingFrameCount() {
        val props = readProps("testV25vbrOld0.mp3")
        assertTrue(props.isVariableBitRate)
        assertEquals(14, props.duration.inWholeSeconds.toInt())
    }

    @Test
    fun mpeg2CbrUsesFrameLengthEstimate() {
        val props = readProps("testV2Cbr128.mp3")
        assertTrue(!props.isVariableBitRate)
        assertTrue(props.duration.inWholeSeconds > 0)
    }

    @Test
    fun xingZeroFrameCountEstimatesFromFollowingAudioFrame() {
        val path = copyToTemp("testV1vbrOld0.mp3", "xing-zero-count")
        try {
            val originalDuration = readProps(path).duration.inWholeSeconds
            val bytes = readAllBytes(path)
            val xingIndex = bytes.indexOfSlice("Xing".encodeToByteArray())
            assertTrue(xingIndex >= 0, "Xing header not found")
            // flags at +4, frame count at +8
            bytes[xingIndex + 8] = 0
            bytes[xingIndex + 9] = 0
            bytes[xingIndex + 10] = 0
            bytes[xingIndex + 11] = 0
            writeAllBytes(path, bytes)
            val patchedDuration = readProps(path).duration.inWholeSeconds
            assertDurationClose(originalDuration.toDouble(), patchedDuration.toDouble(), 2.0)
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun syntheticMpeg2XingFrameCountDuration() {
        // Built from testV2L3Stereo: Info frame with frame count patched to 1000 @ 24 kHz MPEG-2 L3
        val path = copyToTemp("testV2L3Stereo.mp3", "mpeg2-synthetic-xing")
        try {
            val bytes = readAllBytes(path)
            val infoIndex = bytes.indexOfSlice("Info".encodeToByteArray())
            assertTrue(infoIndex >= 0)
            writeInt32Be(bytes, infoIndex + 8, 1000)
            writeAllBytes(path, bytes)
            val props = readProps(path)
            assertEquals(24000, props.sampleRate)
            val expected = 1000 * 576.0 / 24000
            assertDurationClose(expected, props.duration.inWholeSeconds.toDouble(), 1.0)
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    private fun readProps(sample: String): Mp3AudioProperties =
        assertIs<Mp3AudioProperties>(AudioTagger.read(testDataPath(sample)).properties)

    private fun readProps(path: kotlinx.io.files.Path): Mp3AudioProperties =
        assertIs<Mp3AudioProperties>(AudioTagger.read(path).properties)

    private fun durationSeconds(props: Mp3AudioProperties): Double =
        props.duration.inWholeMilliseconds / 1000.0

    private fun assertDurationClose(expected: Double, actual: Double, toleranceSeconds: Double) {
        assertTrue(
            abs(expected - actual) <= toleranceSeconds,
            "expected ~${expected}s but was ${actual}s",
        )
    }

    private fun ByteArray.indexOfSlice(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun writeInt32Be(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 24).toByte()
        data[offset + 1] = (value ushr 16).toByte()
        data[offset + 2] = (value ushr 8).toByte()
        data[offset + 3] = value.toByte()
    }

    private fun readAllBytes(path: kotlinx.io.files.Path): ByteArray =
        openFileIo(path).use { io -> io.readFully(io.size.toInt()) }

    private fun writeAllBytes(path: kotlinx.io.files.Path, data: ByteArray) =
        openFileIo(path, readOnly = false).use { io ->
            io.position = 0
            io.write(data)
            io.truncate(data.size.toLong())
            io.flush()
        }
}
