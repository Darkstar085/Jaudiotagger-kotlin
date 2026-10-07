package org.jaudiotagger.kt.ogg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.audio.ogg.OggOpusTagReader
import org.jaudiotagger.kt.audio.ogg.OggPageHeader
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.io.withFileIo
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.kt.testDataPath

class OpusWriteTest {

    @Test
    fun noOpWriteIsByteIdentical() {
        for (sample in listOf("test-opus.opus", "test-opus-padding.opus", "test-opus-binary-tail.opus")) {
            val path = copyToTemp(sample, "opus-noop")
            val original = readBytes(path)
            val file = AudioTagger.read(path)
            val originalSamples = file.properties.totalSamples
            AudioTagger.write(path, file.tag)
            validateOggPageStructure(path)
            assertEquals(originalSamples, AudioTagger.read(path).properties.totalSamples)
            assertTrue(original.contentEquals(readBytes(path)), sample)
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun editThatFitsPaddingKeepsFileSize() {
        val path = copyToTemp("test-opus-padding.opus", "opus-fit")
        val originalSamples = AudioTagger.read(path).properties.totalSamples
        val originalPages = validateOggPageStructure(path)
        val originalSize = fileSize(path)
        val commentEnd = commentEndPosition(path)
        val originalTail = readBytes(path).copyOfRange(commentEnd, originalSize.toInt())

        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.set(FieldKey.TITLE, "Opus Title" + "x".repeat(100))
        AudioTagger.write(path, tag)

        validateOggPageStructure(path)
        assertEquals(originalSize, fileSize(path))
        assertEquals(originalPages, validateOggPageStructure(path))
        val written = readBytes(path)
        assertTrue(written.copyOfRange(commentEnd, written.size).contentEquals(originalTail))
        val reread = AudioTagger.read(path)
        assertEquals("Opus Title" + "x".repeat(100), reread.tag.first(FieldKey.TITLE))
        assertEquals("Opus Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals(originalSamples, reread.properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun growWithoutPaddingAddsLibopusencPadding() {
        val path = copyToTemp("test-opus.opus", "opus-grow")
        val originalSamples = AudioTagger.read(path).properties.totalSamples

        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.set(FieldKey.TITLE, "Opus Title" + "x".repeat(100))
        AudioTagger.write(path, tag)
        validateOggPageStructure(path)

        withFileIo(path) { io ->
            val header = OggOpusTagReader.readCommentHeader(io)
            val listEnd = requireNotNull(VorbisCommentCodec.commentListEnd(header.commentData))
            val trailing = header.commentData.copyOfRange(listEnd, header.commentData.size)
            assertTrue(trailing.all { byte -> byte == 0.toByte() })
            assertTrue(trailing.size >= 512)
            assertEquals(254, (8 + header.commentData.size) % 255)
        }

        val sizeAfterGrow = fileSize(path)
        val second = AudioTagger.read(path).tag as VorbisCommentTag
        second.set(FieldKey.ARTIST, requireNotNull(second.first(FieldKey.ARTIST)) + "y".repeat(50))
        AudioTagger.write(path, second)
        validateOggPageStructure(path)
        assertEquals(sizeAfterGrow, fileSize(path))
        assertEquals(originalSamples, AudioTagger.read(path).properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun shrinkKeepsPacketLength() {
        val path = copyToTemp("test-opus.opus", "opus-shrink")
        val originalSamples = AudioTagger.read(path).properties.totalSamples
        val originalSize = fileSize(path)

        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.remove(FieldKey.ALBUM)
        AudioTagger.write(path, tag)

        validateOggPageStructure(path)
        assertEquals(originalSize, fileSize(path))
        val reread = AudioTagger.read(path)
        assertEquals(null, reread.tag.first(FieldKey.ALBUM))
        assertEquals(originalSamples, reread.properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun binaryTailIsPreserved() {
        val path = copyToTemp("test-opus-binary-tail.opus", "opus-binary")
        val originalSamples = AudioTagger.read(path).properties.totalSamples

        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.set(FieldKey.TITLE, "Opus Title" + "x".repeat(100))
        AudioTagger.write(path, tag)
        validateOggPageStructure(path)

        withFileIo(path) { io ->
            val header = OggOpusTagReader.readCommentHeader(io)
            val listEnd = requireNotNull(VorbisCommentCodec.commentListEnd(header.commentData))
            val trailing = header.commentData.copyOfRange(listEnd, header.commentData.size)
            assertTrue(trailing.contentEquals("\u0001binary-tail".encodeToByteArray()))
        }
        assertEquals(originalSamples, AudioTagger.read(path).properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun coverSpansPagesAndShrinksToPadding() {
        val path = copyToTemp("test-opus.opus", "opus-cover")
        val originalSamples = AudioTagger.read(path).properties.totalSamples
        val jpeg = readBytes(testDataPath("coverart_large.jpg"))

        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.setArtwork(Artwork(data = jpeg, mimeType = "image/jpeg", description = "cover"))
        AudioTagger.write(path, tag)
        validateOggPageStructure(path)

        val commentPages = commentPageHeaders(path)
        assertTrue(commentPages.size >= 3, "cover should span at least 3 comment pages")
        commentPages.forEachIndexed { index, page ->
            if (index < commentPages.size - 1) {
                assertEquals(-1L, page.absoluteGranulePosition)
            } else {
                assertEquals(0L, page.absoluteGranulePosition)
            }
            if (index >= 1) {
                assertEquals(OggPageHeader.HeaderTypeFlag.CONTINUED_PACKET.fileValue, page.headerType)
            }
        }

        val withCover = AudioTagger.read(path)
        assertTrue(withCover.tag.artworks.first().data.contentEquals(jpeg))
        val sizeWithCover = fileSize(path)

        val withoutCover = withCover.tag as VorbisCommentTag
        withoutCover.clearArtworks()
        AudioTagger.write(path, withoutCover)
        validateOggPageStructure(path)
        assertEquals(sizeWithCover, fileSize(path))
        val reread = AudioTagger.read(path)
        assertTrue(reread.tag.artworks.isEmpty())
        assertEquals("Opus Title", reread.tag.first(FieldKey.TITLE))
        assertEquals("Opus Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals("Opus Album", reread.tag.first(FieldKey.ALBUM))
        assertEquals(originalSamples, reread.properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun writesOpusStreamWithOggExtension() {
        val path = copyToTemp("test-opus-in-ogg.ogg", "opus-ogg-ext")
        val originalSamples = AudioTagger.read(path).properties.totalSamples
        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.set(FieldKey.TITLE, "Edited Title")
        AudioTagger.write(path, tag)
        validateOggPageStructure(path)
        val reread = AudioTagger.read(path)
        assertEquals("Edited Title", reread.tag.first(FieldKey.TITLE))
        assertEquals("Opus Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals(originalSamples, reread.properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun deleteTagLeavesValidStream() {
        val path = copyToTemp("test-opus.opus", "opus-delete")
        val originalSamples = AudioTagger.read(path).properties.totalSamples
        AudioTagger.deleteTag(path)
        validateOggPageStructure(path)
        val reread = AudioTagger.read(path)
        val tag = reread.tag as VorbisCommentTag
        assertEquals(null, tag.first(FieldKey.TITLE))
        assertEquals(null, tag.first(FieldKey.ARTIST))
        assertEquals("jaudiotagger", tag.vendor)
        assertEquals(originalSamples, reread.properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun setTrackKeepsCombinedTotal() {
        val path = copyToTemp("test-opus-track-total.opus", "opus-track-total")
        val originalSamples = AudioTagger.read(path).properties.totalSamples
        val tag = AudioTagger.read(path).tag as VorbisCommentTag
        tag.set(FieldKey.TRACK, "5")
        AudioTagger.write(path, tag)
        validateOggPageStructure(path)
        val reread = AudioTagger.read(path)
        val rereadTag = reread.tag as VorbisCommentTag
        assertEquals("5", rereadTag.first(FieldKey.TRACK))
        assertEquals("12", rereadTag.first(FieldKey.TRACK_TOTAL))
        assertEquals("5/12", rereadTag.firstRaw("TRACKNUMBER"))
        assertEquals(originalSamples, reread.properties.totalSamples)
        SystemFileSystem.delete(path)
    }

    @Test
    fun commentSharingLastPageWithAudioRefusesToWrite() {
        val path = copyToTemp("test-opus-shared-page.opus", "opus-shared")
        val original = readBytes(path)
        val tag = AudioTagger.read(path).tag
        assertFailsWith<CannotWriteException> {
            AudioTagger.write(path, tag)
        }
        validateOggPageStructure(path)
        assertTrue(original.contentEquals(readBytes(path)))
        SystemFileSystem.delete(path)
    }

    private fun commentPageHeaders(path: Path): List<OggPageHeader> {
        return withFileIo(path) { io ->
            val header = OggOpusTagReader.readCommentHeader(io)
            io.position = header.firstPage.startByte
            val pages = ArrayList<OggPageHeader>(header.pageCount)
            for (i in 0 until header.pageCount) {
                val page = OggPageHeader.read(io)
                io.position += page.pageLength
                pages += page
            }
            pages
        }
    }

    private fun commentEndPosition(path: Path): Int {
        return withFileIo(path) { io -> OggOpusTagReader.readCommentHeader(io).endPosition.toInt() }
    }

    private fun fileSize(path: Path): Long = SystemFileSystem.metadataOrNull(path)!!.size

    private fun readBytes(path: Path): ByteArray {
        return SystemFileSystem.source(path).buffered().use { input -> input.readByteArray() }
    }
}
