package org.jaudiotagger.kt.audio.id3

import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.u

/**
 * Minimal ID3v2 header detection, needed because several formats (FLAC, WAV, DSF)
 * may unofficially carry an ID3v2 tag at the start of the file.
 */
internal object Id3v2Detector {

    const val TAG_HEADER_LENGTH = 10

    /**
     * Returns true when an ID3v2 tag starts at [FileIo.position]; on success the
     * position is moved to the first byte after the tag.
     */
    fun skipId3TagIfPresent(io: FileIo): Boolean {
        val start = io.position
        if (io.size - start < TAG_HEADER_LENGTH) return false

        val header = ByteArray(TAG_HEADER_LENGTH)
        if (io.read(header) < TAG_HEADER_LENGTH) {
            io.position = start
            return false
        }
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            io.position = start
            return false
        }

        // bytes 6..9: tag size as a 28-bit synchsafe integer
        val size = (u(header[6]) shl 21) or (u(header[7]) shl 14) or (u(header[8]) shl 7) or u(header[9])
        io.position = start + TAG_HEADER_LENGTH + size
        return true
    }
}
