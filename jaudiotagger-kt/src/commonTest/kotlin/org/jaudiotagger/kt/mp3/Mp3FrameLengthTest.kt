package org.jaudiotagger.kt.mp3

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.audio.id3.Id3v2Detector
import org.jaudiotagger.kt.audio.mp3.MpegFrameHeader
import org.jaudiotagger.kt.audio.mp3.Mp3AudioProperties
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.io.openFileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.testDataPath

class Mp3FrameLengthTest {

    @Test
    fun mpeg2StereoFrameChainReachesValidEnd() {
        for (sample in listOf("test74.mp3", "testV2L3Stereo.mp3")) {
            walkFrameChain(testDataPath(sample), sample)
        }
    }

    @Test
    fun mpeg2MonoFrameChainReachesValidEnd() {
        walkFrameChain(testDataPath("test23.mp3"), "test23.mp3")
    }

    @Test
    fun mpeg1Layer3CbrFrameChainReachesValidEnd() {
        walkFrameChain(testDataPath("testV1Cbr128.mp3"), "testV1Cbr128.mp3")
    }

    @Test
    fun xingZeroFrameCountStereoMpeg2Layer3Duration() {
        val path = copyToTemp("testV2L3Stereo.mp3", "mpeg2-xing-zero-count")
        try {
            val bytes = readAllBytes(path)
            val infoIndex = bytes.indexOfSlice("Info".encodeToByteArray())
            assertTrue(infoIndex >= 0, "Info header not found")
            bytes[infoIndex + 8] = 0
            bytes[infoIndex + 9] = 0
            bytes[infoIndex + 10] = 0
            bytes[infoIndex + 11] = 0
            writeAllBytes(path, bytes)
            val durationMs = readProps(path).duration.inWholeMilliseconds
            assertTrue(
                abs(durationMs - 1300) <= 200,
                "expected ~1300 ms (ffprobe 1.30 s) but was ${durationMs} ms",
            )
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun audioDataStartFollowsId3v2Tag() {
        for (sample in listOf("test74.mp3", "test23.mp3")) {
            val path = testDataPath(sample)
            val props = readProps(path)
            val id3End = id3v2EndOffset(path)
            assertEquals(id3End, props.audioDataStartPosition, "$sample start must follow ID3v2")
        }
    }

    @Test
    fun audioDataStartAtFileBeginningWhenNoId3v2() {
        val props = readProps("testV2L3Stereo.mp3")
        assertEquals(0, props.audioDataStartPosition)
    }

    private fun walkFrameChain(path: Path, label: String) {
        val props = readProps(path)
        openFileIo(path).use { io ->
            var offset = props.audioDataStartPosition
            val fileSize = io.size
            while (offset < fileSize) {
                if (isId3v1TagAt(io, offset, fileSize)) return
                if (isApeTagAt(io, offset)) return
                io.position = offset
                val headerBytes = io.readFully(MpegFrameHeader.HEADER_SIZE)
                assertTrue(MpegFrameHeader.isMpegFrame(headerBytes, 0), "$label: lost sync at $offset")
                val header = MpegFrameHeader.parse(headerBytes, 0)
                val frameLength = header.frameLength
                assertTrue(frameLength > 0, "$label: invalid frame length at $offset")
                offset += frameLength.toLong()
            }
            assertEquals(fileSize, offset, "$label: frame chain must end exactly at EOF")
        }
    }

    private fun isId3v1TagAt(io: org.jaudiotagger.kt.io.FileIo, offset: Long, fileSize: Long): Boolean {
        if (fileSize - offset != 128L) return false
        io.position = offset
        val tag = io.readFully(3)
        return tag[0] == 'T'.code.toByte() && tag[1] == 'A'.code.toByte() && tag[2] == 'G'.code.toByte()
    }

    private fun isApeTagAt(io: org.jaudiotagger.kt.io.FileIo, offset: Long): Boolean {
        if (io.size - offset < 32) return false
        io.position = offset
        val id = io.readFully(8).decodeToString()
        return id == "APETAGEX"
    }

    private fun id3v2EndOffset(path: Path): Long {
        openFileIo(path).use { io ->
            io.position = 0
            assertTrue(Id3v2Detector.skipId3TagIfPresent(io), "expected ID3v2 at start of $path")
            return io.position
        }
    }

    private fun readProps(sample: String): Mp3AudioProperties =
        assertIs<Mp3AudioProperties>(AudioTagger.read(testDataPath(sample)).properties)

    private fun readProps(path: Path): Mp3AudioProperties =
        assertIs<Mp3AudioProperties>(AudioTagger.read(path).properties)

    private fun ByteArray.indexOfSlice(needle: ByteArray): Int {
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun readAllBytes(path: Path) = openFileIo(path).use { it.readFully(it.size.toInt()) }

    private fun writeAllBytes(path: Path, data: ByteArray) =
        openFileIo(path, readOnly = false).use { io ->
            io.position = 0
            io.write(data)
            io.truncate(data.size.toLong())
            io.flush()
        }
}
