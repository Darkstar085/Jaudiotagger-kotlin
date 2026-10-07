package org.jaudiotagger.kt.audio.ogg

import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.u

/**
 * Ogg page header (reference: http://xiph.org/ogg/doc/framing.html).
 *
 * A page starts with the "OggS" capture pattern, a 27-byte fixed header and a
 * segment table of up to 255 lacing values. Packets are built from segments;
 * a lacing value of 255 means the packet continues in the next segment (or page).
 */
class OggPageHeader(val rawHeaderData: ByteArray) {

    /** Position of the last sample contained in this page (64-bit LE). */
    val absoluteGranulePosition: Long = run {
        var value = 0L
        for (i in 0 until FIELD_ABSOLUTE_GRANULE_LENGTH) {
            value = value or (u(rawHeaderData[FIELD_ABSOLUTE_GRANULE_POS + i]).toLong() shl (8 * i))
        }
        value
    }

    val headerType: Byte = rawHeaderData[FIELD_HEADER_TYPE_FLAG_POS]
    val streamSerialNumber: Int = rawHeaderData.readInt32LE(FIELD_STREAM_SERIAL_NO_POS)
    val pageSequence: Int = rawHeaderData.readInt32LE(FIELD_PAGE_SEQUENCE_NO_POS)
    val checksum: Int = rawHeaderData.readInt32LE(FIELD_PAGE_CHECKSUM_POS)

    val segmentTable: ByteArray =
        rawHeaderData.copyOfRange(OGG_PAGE_HEADER_FIXED_LENGTH, rawHeaderData.size)

    /** Total size of the page data following this header. */
    val pageLength: Int

    /** Start and length of each packet in this page, relative to the end of the header. */
    val packetList: List<PacketStartAndLength>

    /** True when the last packet on this page extends onto the next page. */
    val isLastPacketIncomplete: Boolean

    /** Offset of this page header in the file (set when read from a [FileIo]). */
    var startByte: Long = 0

    init {
        val packets = mutableListOf<PacketStartAndLength>()
        var length = 0
        var packetLength = 0
        var segmentLength = -1
        for (segment in segmentTable) {
            segmentLength = u(segment)
            length += segmentLength
            packetLength += segmentLength
            if (segmentLength < MAXIMUM_SEGMENT_SIZE) {
                packets += PacketStartAndLength(length - packetLength, packetLength)
                packetLength = 0
            }
        }
        // a final lacing value of 255 means the packet continues on the next page
        var incomplete = false
        if (segmentLength == MAXIMUM_SEGMENT_SIZE) {
            packets += PacketStartAndLength(length - packetLength, packetLength)
            incomplete = true
        }
        pageLength = length
        packetList = packets
        isLastPacketIncomplete = incomplete
    }

    /** Header length including the segment table. */
    val headerLength: Int get() = rawHeaderData.size

    override fun toString(): String =
        "OggPageHeader(type:$headerType length:$pageLength seq:$pageSequence " +
                "packets:${packetList.size} incomplete:$isLastPacketIncomplete serial:$streamSerialNumber)"

    class PacketStartAndLength(val startPosition: Int, val length: Int) {
        override fun toString(): String = "Pkt(start:$startPosition:length:$length)"
    }

    enum class HeaderTypeFlag(val fileValue: Byte) {
        FRESH_PACKET(0x0),
        CONTINUED_PACKET(0x1),
        START_OF_BITSTREAM(0x2),
        END_OF_BITSTREAM(0x4),
    }

    companion object {
        val CAPTURE_PATTERN =
            byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())

        const val OGG_PAGE_HEADER_FIXED_LENGTH = 27
        const val MAXIMUM_NO_OF_SEGMENT_SIZE = 255
        const val MAXIMUM_SEGMENT_SIZE = 255
        const val MAXIMUM_PAGE_HEADER_SIZE =
            OGG_PAGE_HEADER_FIXED_LENGTH + MAXIMUM_NO_OF_SEGMENT_SIZE
        const val MAXIMUM_PAGE_DATA_SIZE = MAXIMUM_NO_OF_SEGMENT_SIZE * MAXIMUM_SEGMENT_SIZE
        const val MAXIMUM_PAGE_SIZE = MAXIMUM_PAGE_HEADER_SIZE + MAXIMUM_PAGE_DATA_SIZE

        const val FIELD_HEADER_TYPE_FLAG_POS = 5
        const val FIELD_ABSOLUTE_GRANULE_POS = 6
        const val FIELD_STREAM_SERIAL_NO_POS = 14
        const val FIELD_PAGE_SEQUENCE_NO_POS = 18
        const val FIELD_PAGE_CHECKSUM_POS = 22
        const val FIELD_PAGE_SEGMENTS_POS = 26

        const val FIELD_ABSOLUTE_GRANULE_LENGTH = 8

        /**
         * Reads the page header starting at the current position; afterwards the
         * position is just past the header, at the start of the page data.
         */
        fun read(io: FileIo): OggPageHeader {
            val start = io.position

            val pattern = io.readFully(CAPTURE_PATTERN.size)
            if (!pattern.contentEquals(CAPTURE_PATTERN)) {
                throw CannotReadException(
                    "OggS Header could not be found, not an ogg stream: ${pattern.decodeToString()}"
                )
            }

            io.position = start + FIELD_PAGE_SEGMENTS_POS
            val pageSegments = u(io.readFully(1)[0])
            io.position = start

            val header = OggPageHeader(io.readFully(OGG_PAGE_HEADER_FIXED_LENGTH + pageSegments))
            header.startByte = start
            return header
        }
    }
}
