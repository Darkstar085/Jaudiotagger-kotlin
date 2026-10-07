package org.jaudiotagger.kt.audio.ogg

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.ShiftData
import org.jaudiotagger.kt.io.readFully

/**
 * Page-level helpers shared by the Ogg Vorbis and Ogg Opus tag writers.
 */
internal object OggPageWriter {

    class HeaderPages(val data: ByteArray, val pageCount: Int)

    /**
     * Replaces the header pages in `[oldStart, oldEnd)` with [newPages]. The audio moves only when
     * the size changes, and the following pages are renumbered only when the page count changes.
     */
    fun replaceHeaderPages(
        io: FileIo,
        oldStart: Long,
        oldEnd: Long,
        oldPageCount: Int,
        firstPageSequence: Int,
        newPages: HeaderPages,
    ) {
        val delta = newPages.data.size - (oldEnd - oldStart).toInt()
        if (delta > 0) {
            io.position = oldEnd
            ShiftData.shiftDataByOffsetToMakeSpace(io, delta)
        } else if (delta < 0) {
            io.position = oldEnd
            ShiftData.shiftDataByOffsetToShrinkSpace(io, -delta)
        }

        io.position = oldStart
        io.write(newPages.data)

        if (newPages.pageCount != oldPageCount) {
            renumberSubsequentPages(
                io,
                startPosition = oldStart + newPages.data.size,
                nextSequence = firstPageSequence + newPages.pageCount,
            )
        }
        io.flush()
    }

    /**
     * Builds one page: fixed header copied from [template], the given segment
     * table, data written by [writeData], patched sequence number/flags and a
     * freshly stamped CRC.
     */
    fun buildPage(
        template: OggPageHeader,
        segmentTable: ByteArray,
        pageSequence: Int,
        granulePosition: Long,
        continued: Boolean,
        writeData: (Buffer) -> Unit,
    ): ByteArray {
        val buffer = Buffer()
        buffer.write(template.rawHeaderData, 0, OggPageHeader.OGG_PAGE_HEADER_FIXED_LENGTH - 1)
        buffer.writeByte(segmentTable.size.toByte())
        buffer.write(segmentTable)
        writeData(buffer)

        val page = buffer.readByteArray()
        writeInt32LE(page, OggPageHeader.FIELD_PAGE_SEQUENCE_NO_POS, pageSequence)
        writeInt64LE(page, OggPageHeader.FIELD_ABSOLUTE_GRANULE_POS, granulePosition)
        if (continued) {
            page[OggPageHeader.FIELD_HEADER_TYPE_FLAG_POS] =
                OggPageHeader.HeaderTypeFlag.CONTINUED_PACKET.fileValue
        }
        OggCrc.stampCrc(page)
        return page
    }

    /**
     * Lacing values summing to [length]: 255s followed by the remainder. With
     * [quitStream] a length that is an exact multiple of 255 gets a terminating
     * zero so the packet does not appear to continue.
     */
    fun createSegments(length: Int, quitStream: Boolean): ByteArray {
        if (length == 0) {
            return ByteArray(1)
        }
        val size = length / OggPageHeader.MAXIMUM_SEGMENT_SIZE +
            (if (length % OggPageHeader.MAXIMUM_SEGMENT_SIZE == 0 && !quitStream) 0 else 1)
        val result = ByteArray(size)
        for (i in 0 until size - 1) {
            result[i] = 0xFF.toByte()
        }
        result[size - 1] = (length - (size - 1) * OggPageHeader.MAXIMUM_SEGMENT_SIZE).toByte()
        return result
    }

    /**
     * Rewrites the page sequence numbers (and therefore CRCs) of all pages from
     * [startPosition] to the end of the file. An invalid trailing ID3v1 tag is
     * truncated away (files in the wild sometimes carry one).
     */
    private fun renumberSubsequentPages(io: FileIo, startPosition: Long, nextSequence: Int) {
        var position = startPosition
        var sequence = nextSequence

        while (position < io.size) {
            io.position = position
            val header = try {
                OggPageHeader.read(io)
            } catch (e: Exception) {
                io.position = position
                val trailing = io.readFully(minOf(3, (io.size - position).toInt()))
                if (trailing.decodeToString() == "TAG") {
                    io.truncate(position)
                    return
                }
                throw CannotWriteException("Error rewriting page sequence numbers", e)
            }

            val pageSize = header.headerLength + header.pageLength
            io.position = position
            val page = io.readFully(pageSize)
            writeInt32LE(page, OggPageHeader.FIELD_PAGE_SEQUENCE_NO_POS, sequence)
            OggCrc.stampCrc(page)
            io.position = position
            io.write(page)

            position += pageSize
            sequence++
        }
    }

    private fun writeInt32LE(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private fun writeInt64LE(target: ByteArray, offset: Int, value: Long) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
        target[offset + 4] = (value ushr 32).toByte()
        target[offset + 5] = (value ushr 40).toByte()
        target[offset + 6] = (value ushr 48).toByte()
        target[offset + 7] = (value ushr 56).toByte()
    }
}
