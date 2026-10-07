package org.jaudiotagger.kt.audio.ogg

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.decodeLatin1
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * Reads the Vorbis Comment tag from an Ogg Opus stream (RFC 7845).
 *
 * Page 1 carries only the identification header. The comment header packet starts page 2, may
 * span several pages and finishes the page it completes on; audio starts on the next page.
 */
internal object OggOpusTagReader {

    fun read(io: FileIo): VorbisCommentTag {
        return VorbisCommentCodec.decode(readCommentHeader(io).commentData, hasFramingBit = false)
    }

    fun readCommentHeader(io: FileIo): OpusCommentHeader {
        io.position = OggInfoReader.findFirstPageOffset(io)

        val identificationPage = OggPageHeader.read(io)
        io.position += identificationPage.pageLength

        val firstPage = OggPageHeader.read(io)
        val capturePattern = io.readFully(OpusHeader.CAPTURE_PATTERN_LENGTH)
        if (firstPage.packetList.isEmpty() ||
            firstPage.packetList[0].length < OpusHeader.CAPTURE_PATTERN_LENGTH ||
            capturePattern.decodeLatin1() != OpusHeader.TAGS_CAPTURE_PATTERN
        ) {
            throw CannotReadException("Cannot find comment block (no OpusTags header)")
        }

        val buffer = Buffer()
        buffer.write(io.readFully(firstPage.packetList[0].length - OpusHeader.CAPTURE_PATTERN_LENGTH))
        var page = firstPage
        var pageCount = 1
        while (page.packetList.size == 1 && page.isLastPacketIncomplete) {
            page = OggPageHeader.read(io)
            buffer.write(io.readFully(page.packetList[0].length))
            pageCount++
        }

        return OpusCommentHeader(
            commentData = buffer.readByteArray(),
            firstPage = firstPage,
            endPosition = page.startByte + page.headerLength + page.pageLength,
            pageCount = pageCount,
            sharesLastPageWithAudio = page.packetList.size > 1,
        )
    }

    /** The comment packet and the pages it occupies, needed to replace it in place. */
    class OpusCommentHeader(
        /** Packet content after the "OpusTags" magic: the comment list plus any trailing data. */
        val commentData: ByteArray,
        val firstPage: OggPageHeader,
        /** File offset one past the data of the page where the packet completes. */
        val endPosition: Long,
        val pageCount: Int,
        val sharesLastPageWithAudio: Boolean,
    )
}
