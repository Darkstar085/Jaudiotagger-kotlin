package org.jaudiotagger.kt.mp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.random.Random
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.audio.mp3.Mp3AudioProperties
import org.jaudiotagger.kt.io.openFileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.testDataPath

/**
 * Zero-size ID3v2 frames must be skipped (Java [EmptyFrameException]) rather than
 * aborting the frame loop.
 */
class Id3v2EmptyFrameTest {

    @Test
    fun v23EmptyWoafFirstThenMetadataFrames() {
        val tagBytes = buildId3Tag(
            majorVersion = 3,
            frames = listOf(
                emptyV23Frame("WOAF"),
                v23TextFrame("TIT2", "Title"),
                v23TextFrame("TPE1", "Artist"),
                v23TextFrame("TRCK", "12"),
                v23TextFrame("TCON", "Rock"),
            ),
        )
        assertMetadataFields(tagBytes)
    }

    @Test
    fun v23EmptyWoafBetweenMetadataFrames() {
        val tagBytes = buildId3Tag(
            majorVersion = 3,
            frames = listOf(
                v23TextFrame("TIT2", "Title"),
                emptyV23Frame("WOAF"),
                v23TextFrame("TPE1", "Artist"),
                v23TextFrame("TRCK", "12"),
                v23TextFrame("TCON", "Rock"),
            ),
        )
        assertMetadataFields(tagBytes)
    }

    @Test
    fun v24EmptyTyerFirstThenMetadataFrames() {
        val tagBytes = buildId3Tag(
            majorVersion = 4,
            frames = listOf(
                emptyV24Frame("TYER"),
                v24TextFrame("TIT2", "Title"),
                v24TextFrame("TPE1", "Artist"),
                v24TextFrame("TRCK", "12"),
                v24TextFrame("TCON", "Rock"),
            ),
        )
        assertMetadataFields(tagBytes)
    }

    @Test
    fun v24EmptyTyerBetweenMetadataFrames() {
        val tagBytes = buildId3Tag(
            majorVersion = 4,
            frames = listOf(
                v24TextFrame("TIT2", "Title"),
                emptyV24Frame("TYER"),
                v24TextFrame("TPE1", "Artist"),
                v24TextFrame("TRCK", "12"),
                v24TextFrame("TCON", "Rock"),
            ),
        )
        assertMetadataFields(tagBytes)
    }

    private fun assertMetadataFields(tagBytes: ByteArray) {
        val path = mp3WithSyntheticTag(tagBytes)
        try {
            val tag = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
            assertEquals("Title", tag.first(FieldKey.TITLE))
            assertEquals("Artist", tag.first(FieldKey.ARTIST))
            assertEquals("12", tag.first(FieldKey.TRACK))
            assertEquals("Rock", tag.first(FieldKey.GENRE))
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    private fun mp3WithSyntheticTag(tagBytes: ByteArray): Path {
        val audioPath = testDataPath("testV1Cbr128.mp3")
        val audioStart = (AudioTagger.read(audioPath).properties as Mp3AudioProperties).audioDataStartPosition
        val audioBytes = openFileIo(audioPath).use { io ->
            io.position = audioStart
            io.readFully((io.size - audioStart).toInt())
        }
        val target = Path(SystemTemporaryDirectory, "id3-empty-${Random.nextInt()}.mp3")
        SystemFileSystem.sink(target).buffered().use { sink ->
            sink.write(tagBytes)
            sink.write(audioBytes)
        }
        return target
    }

    private fun buildId3Tag(majorVersion: Int, frames: List<ByteArray>): ByteArray {
        val body = frames.reduce { acc, frame -> acc + frame }
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            majorVersion.toByte(), 0, 0,
        ) + synchsafeInt(body.size)
        return header + body
    }

    private fun emptyV23Frame(id: String): ByteArray = buildV23Frame(id, ByteArray(0))

    private fun emptyV24Frame(id: String): ByteArray = buildV24Frame(id, ByteArray(0))

    private fun v23TextFrame(id: String, text: String): ByteArray =
        buildV23Frame(id, byteArrayOf(0) + text.encodeToByteArray())

    private fun v24TextFrame(id: String, text: String): ByteArray =
        buildV24Frame(id, byteArrayOf(0) + text.encodeToByteArray())

    private fun buildV23Frame(id: String, body: ByteArray): ByteArray {
        val frameId = id.padEnd(4, '\u0000').encodeToByteArray().copyOf(4)
        val size = int32Be(body.size)
        return frameId + size + byteArrayOf(0, 0) + body
    }

    private fun buildV24Frame(id: String, body: ByteArray): ByteArray {
        val frameId = id.padEnd(4, '\u0000').encodeToByteArray().copyOf(4)
        return frameId + synchsafeInt(body.size) + byteArrayOf(0, 0) + body
    }

    private fun int32Be(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun synchsafeInt(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7f).toByte(),
        ((value ushr 14) and 0x7f).toByte(),
        ((value ushr 7) and 0x7f).toByte(),
        (value and 0x7f).toByte(),
    )
}
