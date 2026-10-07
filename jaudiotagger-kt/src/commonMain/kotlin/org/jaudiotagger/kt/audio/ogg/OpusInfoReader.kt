package org.jaudiotagger.kt.audio.ogg

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully

/**
 * Reads audio properties from an Ogg Opus stream: channels and pre-skip from the identification
 * header, the sample count from the granule position of the last page.
 */
internal object OpusInfoReader {

    fun read(io: FileIo): AudioProperties {
        io.position = OggInfoReader.findFirstPageOffset(io)
        val firstPage = OggPageHeader.read(io)
        if (firstPage.pageLength < OpusIdentificationHeader.MINIMUM_LENGTH) {
            throw CannotReadException("Invalid Opus identification header")
        }
        val header = OpusIdentificationHeader(io.readFully(firstPage.pageLength))
        if (!header.isValid) {
            throw CannotReadException("Cannot find Opus identification header")
        }

        val totalSamples = maxOf(0L, OggInfoReader.findLastPageGranule(io) - header.preSkip)
        val durationSeconds = totalSamples.toDouble() / OpusHeader.OUTPUT_SAMPLE_RATE

        return AudioProperties(
            format = "OGG",
            encodingType = "Ogg Opus",
            sampleRate = OpusHeader.OUTPUT_SAMPLE_RATE,
            channels = header.channels,
            bitsPerSample = 0,
            bitRate = OggInfoReader.computeBitrate(durationSeconds.toInt(), io.size),
            isVariableBitRate = true,
            isLossless = false,
            duration = durationSeconds.seconds,
            totalSamples = totalSamples,
        )
    }
}
