package org.jaudiotagger.kt.audio.ogg

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * Writes a Vorbis Comment tag into an Ogg Vorbis stream in place.
 *
 * The original jaudiotagger rewrote the whole file into a temp file; here the
 * new header pages (comment + setup) are built in memory, the audio is shifted
 * only when the header area changes size, and page sequence numbers/CRCs of
 * subsequent pages are only rewritten when the header page count changes.
 * This allows editing through a plain file descriptor (Android MediaStore/SAF).
 */
internal object OggVorbisTagWriter {

    fun delete(io: FileIo) {
        write(io, VorbisCommentTag())
    }

    fun write(io: FileIo, tag: VorbisCommentTag) {
        val sizes = OggVorbisTagReader.readOggVorbisHeaderSizes(io)
        val newComment = encodeCommentPacket(tag)

        // Setup header plus any packets sharing its last page; page layout of the
        // original does not matter, we get one contiguous byte blob
        val setupData =
            OggVorbisTagReader.readSetupHeaderAndExtraPackets(io, sizes.setupHeaderStartPosition)

        OggPageWriter.replaceHeaderPages(
            io,
            oldStart = sizes.commentHeaderStartPosition,
            oldEnd = sizes.lastHeaderPageEndPosition,
            oldPageCount = sizes.oldHeaderPageCount,
            firstPageSequence = sizes.secondPageHeader.pageSequence,
            newPages = buildNewHeaderPages(sizes, newComment, setupData),
        )
    }

    /** Comment packet: [type 0x03]["vorbis"][comment data][framing bit 0x01]. */
    private fun encodeCommentPacket(tag: VorbisCommentTag): ByteArray {
        val buffer = Buffer()
        buffer.writeByte(VorbisPacketType.COMMENT_HEADER.type.toByte())
        buffer.write(VorbisHeader.CAPTURE_PATTERN.encodeToByteArray())
        buffer.write(VorbisCommentCodec.encode(tag))
        buffer.writeByte(0x01)
        return buffer.readByteArray()
    }

    /**
     * Builds the replacement pages holding the comment and setup headers,
     * following the same pagination strategy as the original jaudiotagger.
     */
    private fun buildNewHeaderPages(
        sizes: OggVorbisTagReader.OggVorbisHeaderSizes,
        newComment: ByteArray,
        setupData: ByteArray,
    ): OggPageWriter.HeaderPages {
        val template = sizes.secondPageHeader
        val setupSize = sizes.setupHeaderSize
        val extraPackets = sizes.extraPackets
        val out = Buffer()
        var pageCount = 0
        var pageSequence = template.pageSequence

        if (fitsOnASinglePage(newComment.size, setupSize, extraPackets)) {
            // comment and setup (and extras) all go on one page
            val segmentTable = createSegmentTable(newComment.size, setupSize, extraPackets)
            val page = OggPageWriter.buildPage(
                template,
                segmentTable,
                pageSequence,
                template.absoluteGranulePosition,
                continued = false,
            ) { buffer ->
                buffer.write(newComment)
                buffer.write(setupData)
            }
            out.write(page)
            pageCount++
            return OggPageWriter.HeaderPages(out.readByteArray(), pageCount)
        }

        // comment does not fit: spread it over complete pages first
        val completePages = newComment.size / OggPageHeader.MAXIMUM_PAGE_DATA_SIZE
        var commentOffset = 0
        for (i in 0 until completePages) {
            val segmentTable = OggPageWriter.createSegments(
                OggPageHeader.MAXIMUM_PAGE_DATA_SIZE,
                quitStream = false
            )
            val page = OggPageWriter.buildPage(
                template,
                segmentTable,
                pageSequence,
                template.absoluteGranulePosition,
                continued = i != 0,
            ) { buffer ->
                buffer.write(
                    newComment,
                    commentOffset,
                    commentOffset + OggPageHeader.MAXIMUM_PAGE_DATA_SIZE
                )
            }
            out.write(page)
            pageCount++
            pageSequence++
            commentOffset += OggPageHeader.MAXIMUM_PAGE_DATA_SIZE
        }

        val lastCommentPartSize = newComment.size % OggPageHeader.MAXIMUM_PAGE_DATA_SIZE

        if (!fitsOnASinglePage(lastCommentPartSize, setupSize, extraPackets)) {
            // comment tail and setup header go on separate pages
            run {
                val segmentTable =
                    OggPageWriter.createSegments(lastCommentPartSize, quitStream = true)
                val page = OggPageWriter.buildPage(
                    template,
                    segmentTable,
                    pageSequence,
                    template.absoluteGranulePosition,
                    continued = completePages > 0,
                ) { buffer ->
                    buffer.write(newComment, commentOffset, newComment.size)
                }
                out.write(page)
                pageCount++
                pageSequence++
            }
            run {
                val segmentTable = createSegmentTable(setupSize, extraPackets)
                val page = OggPageWriter.buildPage(
                    template,
                    segmentTable,
                    pageSequence,
                    template.absoluteGranulePosition,
                    continued = false,
                ) { buffer ->
                    buffer.write(setupData)
                }
                out.write(page)
                pageCount++
                pageSequence++
            }
        } else {
            // comment tail, setup header and extras all fit on the final page
            val segmentTable = createSegmentTable(lastCommentPartSize, setupSize, extraPackets)
            val page = OggPageWriter.buildPage(
                template,
                segmentTable,
                pageSequence,
                template.absoluteGranulePosition,
                continued = true,
            ) { buffer ->
                buffer.write(newComment, commentOffset, newComment.size)
                buffer.write(setupData)
            }
            out.write(page)
            pageCount++
            pageSequence++
        }

        return OggPageWriter.HeaderPages(out.readByteArray(), pageCount)
    }

    /** Segment table for a page holding the comment (or its tail), setup header and extras. */
    private fun createSegmentTable(
        commentLength: Int,
        setupHeaderLength: Int,
        extraPackets: List<OggPageHeader.PacketStartAndLength>,
    ): ByteArray {
        val buffer = Buffer()
        // comment ends on this page so a length that is a multiple of 255 needs a
        // terminating zero lacing value
        buffer.write(OggPageWriter.createSegments(commentLength, quitStream = true))
        // matches jaudiotagger: without extras the setup segments are left "open"
        buffer.write(
            OggPageWriter.createSegments(
                setupHeaderLength,
                quitStream = extraPackets.isNotEmpty()
            )
        )
        for (packet in extraPackets) {
            buffer.write(OggPageWriter.createSegments(packet.length, quitStream = false))
        }
        return buffer.readByteArray()
    }

    /** Segment table for a page holding only the setup header and extras. */
    private fun createSegmentTable(
        setupHeaderLength: Int,
        extraPackets: List<OggPageHeader.PacketStartAndLength>,
    ): ByteArray {
        val buffer = Buffer()
        buffer.write(OggPageWriter.createSegments(setupHeaderLength, quitStream = true))
        for (packet in extraPackets) {
            buffer.write(OggPageWriter.createSegments(packet.length, quitStream = false))
        }
        return buffer.readByteArray()
    }

    /** Number of lacing values [length] needs when the packet terminates on the page. */
    private fun segmentsNeeded(length: Int): Int {
        if (length == 0) return 1
        var count = length / OggPageHeader.MAXIMUM_SEGMENT_SIZE + 1
        if (length % OggPageHeader.MAXIMUM_SEGMENT_SIZE == 0) count++
        return count
    }

    private fun fitsOnASinglePage(
        commentLength: Int,
        setupHeaderLength: Int,
        extraPackets: List<OggPageHeader.PacketStartAndLength>,
    ): Boolean {
        var totalSegments = segmentsNeeded(commentLength) + segmentsNeeded(setupHeaderLength)
        for (packet in extraPackets) {
            totalSegments += segmentsNeeded(packet.length)
        }
        return totalSegments <= OggPageHeader.MAXIMUM_NO_OF_SEGMENT_SIZE
    }
}
