package org.jaudiotagger.kt.audio.ogg

import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.audio.id3.Id3v2Detector
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import kotlin.time.Duration.Companion.seconds

/**
 * Reads audio properties from an Ogg Vorbis stream: the identification header
 * provides rates and channels; the granule position of the last page provides
 * the total sample count (and thus duration).
 */
internal object OggInfoReader {

    fun read(io: FileIo): AudioProperties {
        val start = findFirstPageOffset(io)

        // Work backwards from end of file looking for the last ogg page; its
        // granule position is the total number of samples
        val totalSamples = findLastPageGranule(io)

        // 1st page holds the identification header
        io.position = start
        val pageHeader = OggPageHeader.read(io)
        if (pageHeader.pageLength < OggPageHeader.OGG_PAGE_HEADER_FIXED_LENGTH) {
            throw CannotReadException("Invalid identification header for this Ogg file")
        }
        val vorbisData = io.readFully(pageHeader.pageLength)
        val identificationHeader = VorbisIdentificationHeader(vorbisData)
        if (!identificationHeader.isValid) {
            throw CannotReadException("Cannot find vorbis identification header")
        }

        val durationSeconds = totalSamples.toDouble() / identificationHeader.audioSampleRate

        val nominal = identificationHeader.bitrateNominal
        val max = identificationHeader.bitrateMaximal
        val min = identificationHeader.bitrateMinimal
        val bitRate: Int
        val isVbr: Boolean
        when {
            nominal != 0 && max == nominal && min == nominal -> {
                bitRate = nominal / 1000
                isVbr = false
            }

            nominal != 0 && max == 0 && min == 0 -> {
                bitRate = nominal / 1000
                isVbr = true
            }

            else -> {
                bitRate = computeBitrate(durationSeconds.toInt(), io.size)
                isVbr = true
            }
        }

        return AudioProperties(
            format = "OGG",
            encodingType = identificationHeader.encodingType,
            sampleRate = identificationHeader.audioSampleRate,
            channels = identificationHeader.audioChannels,
            // Vorbis is defined over 16-bit samples
            bitsPerSample = 16,
            bitRate = bitRate,
            isVariableBitRate = isVbr,
            isLossless = false,
            duration = durationSeconds.seconds,
            totalSamples = totalSamples,
        )
    }

    /** Returns the offset of the first Ogg page, skipping an unofficial leading ID3v2 tag. */
    fun findFirstPageOffset(io: FileIo): Long {
        io.position = 0
        val pattern = ByteArray(OggPageHeader.CAPTURE_PATTERN.size)
        if (io.read(pattern) == pattern.size && pattern.contentEquals(OggPageHeader.CAPTURE_PATTERN)) {
            return 0
        }
        io.position = 0
        if (Id3v2Detector.skipId3TagIfPresent(io)) {
            val afterId3 = io.position
            if (io.read(pattern) == pattern.size && pattern.contentEquals(OggPageHeader.CAPTURE_PATTERN)) {
                return afterId3
            }
        }
        throw CannotReadException("OggS Header could not be found, not an ogg stream")
    }

    /**
     * Scans backwards from the end of the file for the last "OggS" capture pattern
     * and returns that page's absolute granule position.
     */
    fun findLastPageGranule(io: FileIo): Long {
        val fileSize = io.size
        val chunkSize = 64 * 1024
        var chunkEnd = fileSize

        while (chunkEnd > 0) {
            val chunkStart = maxOf(0, chunkEnd - chunkSize)
            // overlap by 3 bytes so a pattern spanning a chunk boundary is found
            val readEnd = minOf(fileSize, chunkEnd + OggPageHeader.CAPTURE_PATTERN.size - 1)
            io.position = chunkStart
            val chunk = io.readFully((readEnd - chunkStart).toInt())

            var i = chunk.size - OggPageHeader.CAPTURE_PATTERN.size
            while (i >= 0) {
                if (chunk[i] == 'O'.code.toByte() &&
                    chunk[i + 1] == 'g'.code.toByte() &&
                    chunk[i + 2] == 'g'.code.toByte() &&
                    chunk[i + 3] == 'S'.code.toByte()
                ) {
                    io.position = chunkStart + i
                    return try {
                        OggPageHeader.read(io).absoluteGranulePosition
                    } catch (_: Exception) {
                        // false positive inside packet data; keep scanning backwards
                        i--
                        continue
                    }
                }
                i--
            }
            chunkEnd = chunkStart
        }
        throw CannotReadException("Could not find the last ogg page to determine stream length")
    }

    // matches jaudiotagger: kilobytes are truncated before multiplying by 8
    fun computeBitrate(lengthSeconds: Int, size: Long): Int {
        // guard against sub-second audio rounding to zero
        val length = if (lengthSeconds == 0) 1 else lengthSeconds
        return ((size / 1000) * 8 / length).toInt()
    }
}
