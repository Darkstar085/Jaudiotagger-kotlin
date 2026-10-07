package org.jaudiotagger.kt.audio.ogg

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * Reads the Vorbis Comment tag from an Ogg Vorbis stream.
 *
 * Page 1 carries the identification header. Page 2 starts with the comment
 * header packet, which may span several pages, followed by the setup header
 * packet (which itself may span pages and share its last page with audio packets).
 */
internal object OggVorbisTagReader {

    fun read(io: FileIo): VorbisCommentTag {
        val rawPacket = readRawCommentPacketData(io)
        return VorbisCommentCodec.decode(rawPacket, hasFramingBit = true)
    }

    /** Reads the comment packet payload (without the 7-byte vorbis header). */
    private fun readRawCommentPacketData(io: FileIo): ByteArray {
        io.position = OggInfoReader.findFirstPageOffset(io)

        // 1st page: identification header, skip it
        val firstPage = OggPageHeader.read(io)
        io.position += firstPage.pageLength

        // 2nd page: comment header
        val secondPage = OggPageHeader.read(io)
        val headerId = io.readFully(VorbisHeader.HEADER_LENGTH)
        if (!VorbisHeader.isHeaderOfType(headerId, VorbisPacketType.COMMENT_HEADER)) {
            throw CannotReadException("Cannot find comment block (no vorbiscomment header)")
        }

        val buffer = Buffer()
        buffer.write(io.readFully(secondPage.packetList[0].length - VorbisHeader.HEADER_LENGTH))

        // another packet on this page, or a complete last packet, ends the comment
        if (secondPage.packetList.size > 1 || !secondPage.isLastPacketIncomplete) {
            return buffer.readByteArray()
        }

        // comment spans further pages
        while (true) {
            val nextPage = OggPageHeader.read(io)
            buffer.write(io.readFully(nextPage.packetList[0].length))
            if (nextPage.packetList.size > 1 || !nextPage.isLastPacketIncomplete) {
                return buffer.readByteArray()
            }
        }
    }

    /**
     * Reads the setup header packet plus any packets following it on its last
     * page, as raw bytes. [setupPageStart] is the offset of the Ogg page on
     * which the setup header starts (it may not be the first packet there).
     */
    fun readSetupHeaderAndExtraPackets(io: FileIo, setupPageStart: Long): ByteArray {
        io.position = setupPageStart
        val setupPage = OggPageHeader.read(io)
        val packetsStart = io.position

        // The setup header is either the first packet on this page, or the second
        // when it shares the page with the tail of the comment header
        val setupPacketIndex: Int
        var headerId = io.readFully(VorbisHeader.HEADER_LENGTH)
        if (VorbisHeader.isHeaderOfType(headerId, VorbisPacketType.SETUP_HEADER)) {
            setupPacketIndex = 0
        } else {
            io.position = packetsStart + setupPage.packetList[0].length
            headerId = io.readFully(VorbisHeader.HEADER_LENGTH)
            if (!VorbisHeader.isHeaderOfType(headerId, VorbisPacketType.SETUP_HEADER)) {
                throw CannotReadException("Unable to find setup header(2), unable to write ogg file")
            }
            setupPacketIndex = 1
        }
        io.position -= VorbisHeader.HEADER_LENGTH

        val buffer = Buffer()
        buffer.write(io.readFully(setupPage.packetList[setupPacketIndex].length))

        // the setup header only continues on the next page when it is the last
        // packet on this page and that packet is incomplete
        if (!setupPage.isLastPacketIncomplete || setupPage.packetList.size > setupPacketIndex + 1) {
            // setup header ends on this page; append any extra packets that follow it
            for (i in setupPacketIndex + 1 until setupPage.packetList.size) {
                buffer.write(io.readFully(setupPage.packetList[i].length))
            }
            return buffer.readByteArray()
        }

        // setup header continues onto subsequent pages
        while (true) {
            val nextPage = OggPageHeader.read(io)
            buffer.write(io.readFully(nextPage.packetList[0].length))
            if (nextPage.packetList.size > 1 || !nextPage.isLastPacketIncomplete) {
                for (i in 1 until nextPage.packetList.size) {
                    buffer.write(io.readFully(nextPage.packetList[i].length))
                }
                return buffer.readByteArray()
            }
        }
    }

    /**
     * Sizes and positions of the vorbis comment and setup headers, needed for
     * rewriting. Sizes include the 7-byte vorbis header of each packet.
     */
    class OggVorbisHeaderSizes(
        val commentHeaderStartPosition: Long,
        val setupHeaderStartPosition: Long,
        val commentHeaderSize: Int,
        val setupHeaderSize: Int,
        /** Packets on the same page after the setup header (usually none). */
        val extraPackets: List<OggPageHeader.PacketStartAndLength>,
        /** Page sequence number and raw header of the page the comment starts on. */
        val secondPageHeader: OggPageHeader,
        /** File offset one past the data of the last page holding header packets. */
        val lastHeaderPageEndPosition: Long,
        /** Number of pages occupied by the comment and setup headers. */
        val oldHeaderPageCount: Int,
    ) {
        val extraPacketDataSize: Int get() = extraPackets.sumOf { it.length }
    }

    fun readOggVorbisHeaderSizes(io: FileIo): OggVorbisHeaderSizes {
        io.position = OggInfoReader.findFirstPageOffset(io)

        // 1st page: identification header
        val firstPage = OggPageHeader.read(io)
        io.position += firstPage.pageLength

        // 2nd page: comment header (may span pages, may share its last page with the setup header)
        var pageHeader = OggPageHeader.read(io)
        val secondPageHeader = pageHeader
        val commentHeaderStartPosition = pageHeader.startByte
        var pageCount = 1

        val headerId = io.readFully(VorbisHeader.HEADER_LENGTH)
        if (!VorbisHeader.isHeaderOfType(headerId, VorbisPacketType.COMMENT_HEADER)) {
            throw CannotReadException("Cannot find comment block (no vorbiscomment header)")
        }
        io.position -= VorbisHeader.HEADER_LENGTH

        var commentHeaderSize = 0
        while (true) {
            commentHeaderSize += pageHeader.packetList[0].length
            io.position += pageHeader.packetList[0].length
            if (pageHeader.packetList.size > 1 || !pageHeader.isLastPacketIncomplete) {
                break
            }
            pageHeader = OggPageHeader.read(io)
            pageCount++
        }

        val setupHeaderStartPosition: Long
        var setupHeaderSize: Int
        var extraPackets: List<OggPageHeader.PacketStartAndLength> = emptyList()

        // the index of the setup packet on its first page: 0 when the comment ended
        // exactly at a page boundary, 1 when it shares the page with the comment tail
        val setupPacketIndex: Int
        if (pageHeader.packetList.size == 1) {
            pageHeader = OggPageHeader.read(io)
            pageCount++
            setupPacketIndex = 0
        } else {
            setupPacketIndex = 1
        }

        val setupId = io.readFully(VorbisHeader.HEADER_LENGTH)
        if (!VorbisHeader.isHeaderOfType(setupId, VorbisPacketType.SETUP_HEADER)) {
            throw CannotReadException("Unable to find vorbis setup header")
        }
        io.position -= VorbisHeader.HEADER_LENGTH
        setupHeaderStartPosition = pageHeader.startByte

        setupHeaderSize = pageHeader.packetList[setupPacketIndex].length
        io.position += setupHeaderSize

        if (pageHeader.packetList.size > setupPacketIndex + 1 || !pageHeader.isLastPacketIncomplete) {
            if (pageHeader.packetList.size > setupPacketIndex + 1) {
                extraPackets = pageHeader.packetList.drop(setupPacketIndex + 1)
            }
        } else {
            // setup header continues onto further pages
            pageHeader = OggPageHeader.read(io)
            pageCount++
            while (true) {
                setupHeaderSize += pageHeader.packetList[0].length
                io.position += pageHeader.packetList[0].length
                if (pageHeader.packetList.size > 1 || !pageHeader.isLastPacketIncomplete) {
                    if (pageHeader.packetList.size > 1) {
                        extraPackets = pageHeader.packetList.drop(1)
                    }
                    break
                }
                pageHeader = OggPageHeader.read(io)
                pageCount++
            }
        }

        val lastHeaderPageEndPosition =
            pageHeader.startByte + pageHeader.headerLength + pageHeader.pageLength

        return OggVorbisHeaderSizes(
            commentHeaderStartPosition = commentHeaderStartPosition,
            setupHeaderStartPosition = setupHeaderStartPosition,
            commentHeaderSize = commentHeaderSize,
            setupHeaderSize = setupHeaderSize,
            extraPackets = extraPackets,
            secondPageHeader = secondPageHeader,
            lastHeaderPageEndPosition = lastHeaderPageEndPosition,
            oldHeaderPageCount = pageCount,
        )
    }
}
