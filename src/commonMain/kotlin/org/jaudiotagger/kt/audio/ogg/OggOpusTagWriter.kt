package org.jaudiotagger.kt.audio.ogg

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.u
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * Writes a Vorbis Comment tag into an Ogg Opus stream in place.
 *
 * Zero padding after the comment list absorbs size changes, so an edit that fits rewrites only
 * the comment pages; comments that outgrow it get fresh padding the way libopusenc sizes it.
 * Binary data flagged by the low bit of its first byte is kept, as RFC 7845 §5.2 asks.
 */
internal object OggOpusTagWriter {

    fun delete(io: FileIo) {
        write(io, VorbisCommentTag())
    }

    fun write(io: FileIo, tag: VorbisCommentTag) {
        val header = OggOpusTagReader.readCommentHeader(io)
        if (header.sharesLastPageWithAudio) {
            throw CannotWriteException("Opus comment header shares its last page with audio, unable to write opus file")
        }
        val packet = buildCommentPacket(tag, header.commentData)
        OggPageWriter.replaceHeaderPages(
            io,
            oldStart = header.firstPage.startByte,
            oldEnd = header.endPosition,
            oldPageCount = header.pageCount,
            firstPageSequence = header.firstPage.pageSequence,
            newPages = buildHeaderPages(header.firstPage, packet),
        )
    }

    private fun buildCommentPacket(tag: VorbisCommentTag, oldCommentData: ByteArray): ByteArray {
        val comments = VorbisCommentCodec.encode(tag)
        val buffer = Buffer()
        buffer.write(OpusHeader.TAGS_CAPTURE_PATTERN.encodeToByteArray())
        buffer.write(comments)

        val oldListEnd = VorbisCommentCodec.commentListEnd(oldCommentData) ?: oldCommentData.size
        val hasBinaryData = oldListEnd < oldCommentData.size && (u(oldCommentData[oldListEnd]) and 0x01) == 1
        if (hasBinaryData) {
            buffer.write(oldCommentData, oldListEnd, oldCommentData.size)
            return buffer.readByteArray()
        }

        val unpaddedLength = OpusHeader.CAPTURE_PATTERN_LENGTH + comments.size
        val oldPacketLength = OpusHeader.CAPTURE_PATTERN_LENGTH + oldCommentData.size
        val packetLength = if (unpaddedLength <= oldPacketLength) oldPacketLength else paddedPacketLength(unpaddedLength)
        buffer.write(ByteArray(packetLength - unpaddedLength))
        return buffer.readByteArray()
    }

    /** At least [MINIMUM_PADDING] spare bytes, rounded up so the packet fills its last lacing segment. */
    private fun paddedPacketLength(length: Int): Int {
        val segment = OggPageHeader.MAXIMUM_SEGMENT_SIZE
        return (length + MINIMUM_PADDING + segment) / segment * segment - 1
    }

    /**
     * Full pages first, then a final page that completes the packet. The final page holds a
     * single zero lacing value when the packet is an exact multiple of the page size.
     */
    private fun buildHeaderPages(template: OggPageHeader, packet: ByteArray): OggPageWriter.HeaderPages {
        val out = Buffer()
        val fullPages = packet.size / OggPageHeader.MAXIMUM_PAGE_DATA_SIZE
        for (i in 0 until fullPages) {
            val start = i * OggPageHeader.MAXIMUM_PAGE_DATA_SIZE
            val segmentTable = OggPageWriter.createSegments(OggPageHeader.MAXIMUM_PAGE_DATA_SIZE, quitStream = false)
            val page = OggPageWriter.buildPage(
                template,
                segmentTable,
                template.pageSequence + i,
                NO_PACKET_COMPLETES_GRANULE,
                continued = i != 0,
            ) { buffer ->
                buffer.write(packet, start, start + OggPageHeader.MAXIMUM_PAGE_DATA_SIZE)
            }
            out.write(page)
        }

        val lastStart = fullPages * OggPageHeader.MAXIMUM_PAGE_DATA_SIZE
        val lastSegmentTable = OggPageWriter.createSegments(packet.size - lastStart, quitStream = true)
        val lastPage = OggPageWriter.buildPage(
            template,
            lastSegmentTable,
            template.pageSequence + fullPages,
            COMMENT_COMPLETES_GRANULE,
            continued = fullPages > 0,
        ) { buffer ->
            buffer.write(packet, lastStart, packet.size)
        }
        out.write(lastPage)
        return OggPageWriter.HeaderPages(out.readByteArray(), fullPages + 1)
    }

    private const val MINIMUM_PADDING = 512

    /** RFC 7845 §3: a page spanned entirely by one packet has no granule position. */
    private const val NO_PACKET_COMPLETES_GRANULE = -1L

    /** RFC 7845 §3: the page where the comment header completes has granule position zero. */
    private const val COMMENT_COMPLETES_GRANULE = 0L
}
