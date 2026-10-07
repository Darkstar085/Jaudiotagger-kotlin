package org.jaudiotagger.kt.ogg

import kotlinx.io.files.Path
import org.jaudiotagger.kt.audio.ogg.OggCrc
import org.jaudiotagger.kt.audio.ogg.OggPageHeader
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.withFileIo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Walks every page of an Ogg file asserting structural invariants:
 * pages are contiguous to EOF, sequence numbers increase by one and the
 * stored CRC of each page matches a freshly computed one.
 */
fun validateOggPageStructure(path: Path): Int {
    var pageCount = 0
    withFileIo(path) { io ->
        var position = 0L
        var lastSequence: Int? = null
        while (position < io.size) {
            io.position = position
            val header = OggPageHeader.read(io)

            val packetsTotal = header.packetList.sumOf { it.length }
            assertEquals(header.pageLength, packetsTotal, "packet lengths must sum to page length")

            lastSequence?.let {
                assertEquals(it + 1, header.pageSequence, "page sequence must be contiguous")
            }
            lastSequence = header.pageSequence

            val pageSize = header.headerLength + header.pageLength
            io.position = position
            val page = io.readFully(pageSize)
            val storedCrc = page.copyOfRange(
                OggPageHeader.FIELD_PAGE_CHECKSUM_POS,
                OggPageHeader.FIELD_PAGE_CHECKSUM_POS + 4,
            )
            OggCrc.stampCrc(page)
            val computedCrc = page.copyOfRange(
                OggPageHeader.FIELD_PAGE_CHECKSUM_POS,
                OggPageHeader.FIELD_PAGE_CHECKSUM_POS + 4,
            )
            assertTrue(
                storedCrc.contentEquals(computedCrc),
                "CRC mismatch on page seq ${header.pageSequence} at offset $position",
            )

            position += pageSize
            pageCount++
        }
        assertEquals(io.size, position, "pages must end exactly at end of file")
    }
    return pageCount
}
