package org.jaudiotagger.kt.audio.ogg

import org.jaudiotagger.kt.io.decodeLatin1
import org.jaudiotagger.kt.io.readUInt16LE
import org.jaudiotagger.kt.io.u

/** Constants of the Ogg Opus headers (RFC 7845 §5). */
internal object OpusHeader {
    const val HEAD_CAPTURE_PATTERN = "OpusHead"
    const val TAGS_CAPTURE_PATTERN = "OpusTags"
    const val CAPTURE_PATTERN_LENGTH = 8

    /** Opus always decodes at 48 kHz, and granule positions count samples at this rate. */
    const val OUTPUT_SAMPLE_RATE = 48_000
}

/**
 * Opus identification header (RFC 7845 §5.1): "OpusHead", version uint8, channel count uint8,
 * pre-skip uint16 LE, input sample rate uint32 LE, output gain int16 LE, mapping family uint8.
 * The input sample rate describes the encoder's source, not the stream, so it is not read.
 */
internal class OpusIdentificationHeader(b: ByteArray) {

    private val version: Int = u(b[FIELD_VERSION_POS])
    val channels: Int = u(b[FIELD_CHANNELS_POS])
    val preSkip: Int = b.readUInt16LE(FIELD_PRE_SKIP_POS)

    // any version with a zero high nibble is compatible with version 1
    val isValid: Boolean =
        b.decodeLatin1(0, OpusHeader.CAPTURE_PATTERN_LENGTH) == OpusHeader.HEAD_CAPTURE_PATTERN &&
                (version and 0xF0) == 0 &&
                channels > 0

    companion object {
        const val MINIMUM_LENGTH = 19

        private const val FIELD_VERSION_POS = 8
        private const val FIELD_CHANNELS_POS = 9
        private const val FIELD_PRE_SKIP_POS = 10
    }
}
