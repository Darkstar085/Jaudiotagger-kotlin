package org.jaudiotagger.kt.audio.flac

import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.audio.id3.Id3v2Detector
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully

/**
 * Locates the start of the FLAC stream ("fLaC" marker), allowing for an
 * unofficial ID3v2 tag before it. After [findStream] the [FileIo] position is
 * just past the marker and [startOfFlacInFile] holds the marker offset.
 */
internal class FlacStreamReader(private val io: FileIo) {

    var startOfFlacInFile: Long = 0
        private set

    fun findStream() {
        if (io.size == 0L) {
            throw CannotReadException("Error: File empty")
        }
        io.position = 0

        if (isFlacHeader()) {
            startOfFlacInFile = 0
            return
        }

        // Unofficially an ID3v2 tag may precede the FLAC stream
        io.position = 0
        if (Id3v2Detector.skipId3TagIfPresent(io) && isFlacHeader()) {
            startOfFlacInFile = io.position - FLAC_STREAM_IDENTIFIER_LENGTH
            return
        }
        throw CannotReadException("Flac Header not found, not a flac file")
    }

    private fun isFlacHeader(): Boolean {
        if (io.size - io.position < FLAC_STREAM_IDENTIFIER_LENGTH) return false
        return io.readFully(FLAC_STREAM_IDENTIFIER_LENGTH)
            .decodeToString() == FLAC_STREAM_IDENTIFIER
    }

    companion object {
        const val FLAC_STREAM_IDENTIFIER_LENGTH = 4
        const val FLAC_STREAM_IDENTIFIER = "fLaC"
    }
}
