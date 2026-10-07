package org.jaudiotagger.kt.audio.ape

import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.tag.ape.ApeTag
import org.jaudiotagger.kt.tag.ape.ApeTagCodec
import org.jaudiotagger.kt.tag.ape.ApeTagFooter

/**
 * Reads and writes trailing APEv2 tags, as used by Monkey's Audio and WavPack.
 *
 * Trailing metadata order in such files is: audio, APEv2 tag, optional Lyrics3
 * block, optional ID3v1 tag. Rewrites preserve the Lyrics3/ID3v1 blocks and any
 * padding between the tag and them.
 */
internal object ApeTagIo {

    private const val ID3V1_LENGTH = 128
    private val LYRICS_MARKER = "LYRICS200".encodeToByteArray()
    private const val LYRICS_SIZE_LENGTH = 6

    class TagLocation(
        val dataStart: Long,
        val dataEnd: Long,
        val headerOffset: Long, // -1 when absent
        val footerOffset: Long, // -1 when absent
        val isAtStart: Boolean,
    ) {
        val removalStart: Long get() = if (headerOffset >= 0) headerOffset else dataStart
        val removalEnd: Long
            get() = if (footerOffset >= 0) footerOffset + ApeTagFooter.FOOTER_SIZE else dataEnd
    }

    class ScanResult(
        val location: TagLocation?,
        val id3Data: ByteArray?,
        val id3Offset: Long,
        val lyricsData: ByteArray?,
        val lyricsOffset: Long,
        val padding: ByteArray,
        val fileLength: Long,
    ) {
        /** Where new tag bytes go: replaces the old tag, else before Lyrics3/ID3, else EOF. */
        val insertionOffset: Long
            get() = when {
                location != null && !location.isAtStart -> location.removalStart
                lyricsData != null -> lyricsOffset
                id3Data != null -> id3Offset
                else -> fileLength
            }
    }

    fun scan(io: FileIo): ScanResult {
        // trailing ID3v1
        var id3Data: ByteArray? = null
        var id3Offset = -1L
        var nextMetadataOffset = io.size
        if (io.size >= ID3V1_LENGTH) {
            io.position = io.size - ID3V1_LENGTH
            val candidate = io.readFully(ID3V1_LENGTH)
            if (candidate[0] == 'T'.code.toByte() && candidate[1] == 'A'.code.toByte() && candidate[2] == 'G'.code.toByte()) {
                id3Data = candidate
                id3Offset = io.size - ID3V1_LENGTH
                nextMetadataOffset = id3Offset
            }
        }

        // Lyrics3v2 block before the ID3v1 tag: payload, 6 ASCII digits of size, "LYRICS200"
        var lyricsData: ByteArray? = null
        var lyricsOffset = -1L
        if (nextMetadataOffset >= LYRICS_MARKER.size + LYRICS_SIZE_LENGTH) {
            val markerPos = nextMetadataOffset - LYRICS_MARKER.size
            io.position = markerPos
            if (io.readFully(LYRICS_MARKER.size).contentEquals(LYRICS_MARKER)) {
                val sizePos = markerPos - LYRICS_SIZE_LENGTH
                io.position = sizePos
                val payloadSize = io.readFully(LYRICS_SIZE_LENGTH).decodeToString().toIntOrNull()
                if (payloadSize != null && sizePos - payloadSize >= 0) {
                    lyricsOffset = sizePos - payloadSize
                    io.position = lyricsOffset
                    lyricsData = io.readFully((nextMetadataOffset - lyricsOffset).toInt())
                    nextMetadataOffset = lyricsOffset
                }
            }
        }

        var location = locateTrailingTag(io, nextMetadataOffset)

        // preserve padding between the tag end and the following metadata block
        var padding = ByteArray(0)
        if (location != null && !location.isAtStart && location.footerOffset >= 0) {
            val afterTag = location.removalEnd
            if (afterTag < nextMetadataOffset) {
                io.position = afterTag
                padding = io.readFully((nextMetadataOffset - afterTag).toInt())
            }
        }

        if (location == null) {
            location = locateStartingTag(io)
        }

        return ScanResult(location, id3Data, id3Offset, lyricsData, lyricsOffset, padding, io.size)
    }

    private fun locateTrailingTag(io: FileIo, limit: Long): TagLocation? {
        if (limit < ApeTagFooter.FOOTER_SIZE) return null

        // normal case first: footer sits right at the limit
        val candidate = limit - ApeTagFooter.FOOTER_SIZE
        io.position = candidate
        val footer = ApeTagFooter.parse(io.readFully(ApeTagFooter.FOOTER_SIZE))
        if (footer == null || !footer.isValid || footer.isHeader) {
            return null
        }
        // footer.size covers items + footer but never the optional 32-byte header,
        // so the items always start at footerOffset - (size - 32)
        val dataLength = footer.size - ApeTagFooter.FOOTER_SIZE
        val dataStart = footer.calculateTagStart(candidate)
        if (dataLength < 0 || dataStart < 0) return null
        val headerOffset = if (footer.hasHeader) dataStart - ApeTagFooter.FOOTER_SIZE else -1L
        if (footer.hasHeader && headerOffset < 0) return null
        return TagLocation(dataStart, candidate, headerOffset, candidate, isAtStart = false)
    }

    /** APEv2 also allows a tag at the very start of the file (before the audio). */
    private fun locateStartingTag(io: FileIo): TagLocation? {
        if (io.size < ApeTagFooter.FOOTER_SIZE) return null
        io.position = 0
        val header = ApeTagFooter.parse(io.readFully(ApeTagFooter.FOOTER_SIZE)) ?: return null
        if (!header.isValid || !header.isHeader) return null

        val dataStart = ApeTagFooter.FOOTER_SIZE.toLong()
        var dataLength = header.size.toLong()
        if (header.hasFooter) dataLength -= ApeTagFooter.FOOTER_SIZE
        if (dataLength < 0) return null
        val dataEnd = dataStart + dataLength
        val footerOffset = if (header.hasFooter) dataEnd else -1
        if (header.hasFooter && footerOffset + ApeTagFooter.FOOTER_SIZE > io.size) return null
        return TagLocation(dataStart, dataEnd, 0, footerOffset, isAtStart = true)
    }

    fun readTag(io: FileIo): ApeTag {
        val location = scan(io).location ?: return ApeTag()
        io.position = location.dataStart
        val data = io.readFully((location.dataEnd - location.dataStart).toInt())
        return ApeTagCodec.decodeItems(data)
    }

    fun writeTag(io: FileIo, tag: ApeTag) {
        val payload = ApeTagCodec.encodeTag(tag)
        if (payload.isEmpty()) {
            deleteTag(io)
            return
        }

        var scan = scan(io)
        // a tag at the start of the file is not preserved: remove and re-scan
        if (scan.location?.isAtStart == true) {
            removeRange(io, scan.location.removalStart, scan.location.removalEnd)
            scan = scan(io)
        }

        io.truncate(scan.insertionOffset)
        io.position = scan.insertionOffset
        io.write(payload)
        writeTrailingMetadata(io, scan)
        io.flush()
    }

    fun deleteTag(io: FileIo) {
        val scan = scan(io)
        val location = scan.location ?: return
        if (location.isAtStart) {
            removeRange(io, location.removalStart, location.removalEnd)
            io.flush()
            return
        }
        io.truncate(location.removalStart)
        io.position = location.removalStart
        writeTrailingMetadata(io, scan)
        io.flush()
    }

    private fun writeTrailingMetadata(io: FileIo, scan: ScanResult) {
        if (scan.padding.isNotEmpty()) io.write(scan.padding)
        scan.lyricsData?.let { io.write(it) }
        scan.id3Data?.let { io.write(it) }
    }

    /** Removes [start, end) by shifting the remainder of the file down. */
    private fun removeRange(io: FileIo, start: Long, end: Long) {
        if (start < 0 || end <= start) return
        val buffer = ByteArray(64 * 1024)
        var readPos = end
        var writePos = start
        val fileLength = io.size
        while (readPos < fileLength) {
            val chunk = minOf(buffer.size.toLong(), fileLength - readPos).toInt()
            io.position = readPos
            val read = io.read(buffer, 0, chunk)
            if (read <= 0) break
            io.position = writePos
            io.write(buffer, 0, read)
            readPos += read
            writePos += read
        }
        io.truncate(writePos)
    }
}
