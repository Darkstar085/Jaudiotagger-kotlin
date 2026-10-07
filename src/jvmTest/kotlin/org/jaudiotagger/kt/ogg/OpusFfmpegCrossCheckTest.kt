package org.jaudiotagger.kt.ogg

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.kt.testDataPath
import org.junit.Assume
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FFMPEG_DECODE_COUNT = 144000L // ffmpeg decode count

class OpusFfmpegCrossCheckTest {

    @Test
    fun paddingEditIsAcceptedByFfmpeg() {
        assumeFfprobeAvailable()
        val path = copyToTemp("test-opus-padding.opus", "opus-ffmpeg-pad")
        try {
            val tag = AudioTagger.read(path).tag as VorbisCommentTag
            tag.set(FieldKey.TITLE, "Opus Title" + "x".repeat(100))
            AudioTagger.write(path, tag)
            assertFfmpegAccepts(path, "Opus Title" + "x".repeat(100), expectCover = false)
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun multiPageCoverIsAcceptedByFfmpeg() {
        assumeFfprobeAvailable()
        val path = copyToTemp("test-opus.opus", "opus-ffmpeg-cover")
        try {
            val jpeg = File(testDataPath("coverart_large.jpg").toString()).readBytes()
            val tag = AudioTagger.read(path).tag as VorbisCommentTag
            tag.set(FieldKey.TITLE, "Cover Title")
            tag.setArtwork(Artwork(data = jpeg, mimeType = "image/jpeg", description = "cover"))
            AudioTagger.write(path, tag)
            assertFfmpegAccepts(path, "Cover Title", expectCover = true)
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun combinedTrackEditIsAcceptedByFfmpeg() {
        assumeFfprobeAvailable()
        val path = copyToTemp("test-opus-track-total.opus", "opus-ffmpeg-track")
        try {
            val tag = AudioTagger.read(path).tag as VorbisCommentTag
            tag.set(FieldKey.TRACK, "5")
            AudioTagger.write(path, tag)
            assertFfmpegAccepts(path, "Opus Title", expectCover = false)
            val file = File(path.toString())
            val probedTrack = run(
                "ffprobe", "-v", "error",
                "-select_streams", "a",
                "-show_entries", "stream_tags=track",
                "-of", "default=nw=1:nk=1",
                file.absolutePath,
                captureStdout = true,
            )
            assertEquals(0, probedTrack.exitCode)
            assertEquals("5/12", probedTrack.stdout.decodeToString().trim())
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    private fun assumeFfprobeAvailable() {
        Assume.assumeTrue(
            try {
                val process =
                    ProcessBuilder("ffprobe", "-version").redirectErrorStream(true).start()
                process.inputStream.readBytes()
                process.waitFor() == 0
            } catch (_: Exception) {
                false
            },
        )
    }

    private fun assertFfmpegAccepts(path: Path, title: String, expectCover: Boolean) {
        val file = File(path.toString())
        val nullCheck = run(
            "ffmpeg", "-v", "error", "-i", file.absolutePath, "-f", "null", "-",
            captureStdout = false,
        )
        assertEquals(0, nullCheck.exitCode)
        assertEquals("", nullCheck.stderr)

        val pcm = run(
            "ffmpeg", "-v", "error", "-i", file.absolutePath,
            "-map", "0:a", "-f", "s16le", "-acodec", "pcm_s16le", "-",
            captureStdout = true,
        )
        assertEquals(0, pcm.exitCode)
        assertEquals("", pcm.stderr)
        assertEquals(FFMPEG_DECODE_COUNT, pcm.stdout.size / (2L * 2L))

        val probedTitle = run(
            "ffprobe", "-v", "error",
            "-select_streams", "a",
            "-show_entries", "stream_tags=title",
            "-of", "default=nw=1:nk=1",
            file.absolutePath,
            captureStdout = true,
        )
        assertEquals(0, probedTitle.exitCode)
        assertEquals(title, probedTitle.stdout.decodeToString().trim())

        if (expectCover) {
            val streams = run(
                "ffprobe", "-v", "error",
                "-show_entries", "stream=codec_type:stream_disposition=attached_pic",
                "-of", "csv=p=0",
                file.absolutePath,
                captureStdout = true,
            )
            assertEquals(0, streams.exitCode)
            val lines = streams.stdout.decodeToString().trim().lines()
            assertTrue(
                lines.any { line -> line == "video,1" || line.startsWith("video,1,") },
                "expected attached picture, got: $lines",
            )
        }
    }

    private fun run(vararg command: String, captureStdout: Boolean): CommandResult {
        val process = ProcessBuilder(*command).start()
        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes()
        val capturedStdout = if (captureStdout) stdout else ByteArray(0)
        return CommandResult(process.waitFor(), capturedStdout, stderr.decodeToString())
    }

    private class CommandResult(val exitCode: Int, val stdout: ByteArray, val stderr: String)
}
