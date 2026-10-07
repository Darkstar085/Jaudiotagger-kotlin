package org.jaudiotagger.kt.audio.ogg

import org.jaudiotagger.kt.io.readInt32LE
import org.jaudiotagger.kt.io.u

/**
 * Constants shared by all Vorbis headers: a one-byte packet type followed by
 * the "vorbis" capture pattern.
 */
internal object VorbisHeader {
    const val CAPTURE_PATTERN = "vorbis"

    const val FIELD_PACKET_TYPE_POS = 0
    const val FIELD_CAPTURE_PATTERN_POS = 1

    const val FIELD_PACKET_TYPE_LENGTH = 1
    const val FIELD_CAPTURE_PATTERN_LENGTH = 6

    /** Combined length of packet type byte and capture pattern. */
    const val HEADER_LENGTH = FIELD_PACKET_TYPE_LENGTH + FIELD_CAPTURE_PATTERN_LENGTH

    fun isHeaderOfType(headerData: ByteArray, packetType: VorbisPacketType): Boolean {
        if (headerData.size < HEADER_LENGTH) return false
        if (headerData[FIELD_PACKET_TYPE_POS].toInt() != packetType.type) return false
        val pattern = headerData.decodeToString(
            FIELD_CAPTURE_PATTERN_POS,
            FIELD_CAPTURE_PATTERN_POS + FIELD_CAPTURE_PATTERN_LENGTH,
        )
        return pattern == CAPTURE_PATTERN
    }
}

/**
 * Vorbis packet types; a Vorbis stream has one instance of each header type
 * followed by many audio packets.
 */
internal enum class VorbisPacketType(val type: Int) {
    AUDIO(0),
    IDENTIFICATION_HEADER(1),
    COMMENT_HEADER(3),
    SETUP_HEADER(5),
}

/**
 * Vorbis identification header: declares the stream as Vorbis and carries
 * sample rate, channel count and bitrate hints.
 *
 * Layout (after the 7-byte vorbis header):
 * version uint32 LE, channels uint8, sampleRate uint32 LE,
 * bitrateMax int32 LE, bitrateNominal int32 LE, bitrateMin int32 LE,
 * blocksizes uint8, framing flag uint8.
 */
internal class VorbisIdentificationHeader(b: ByteArray) {

    val vorbisVersion: Int = b.readInt32LE(7)
    val audioChannels: Int = u(b[FIELD_AUDIO_CHANNELS_POS])
    val audioSampleRate: Int = b.readInt32LE(12)
    val bitrateMinimal: Int = b.readInt32LE(16)
    val bitrateNominal: Int = b.readInt32LE(20)
    val bitrateMaximal: Int = b.readInt32LE(24)

    val isValid: Boolean =
        VorbisHeader.isHeaderOfType(b, VorbisPacketType.IDENTIFICATION_HEADER) &&
            b.size > FIELD_FRAMING_FLAG_POS &&
            b[FIELD_FRAMING_FLAG_POS].toInt() != 0

    val encodingType: String get() = "Ogg Vorbis v${vorbisVersion + 1}"

    companion object {
        const val FIELD_AUDIO_CHANNELS_POS = 11
        const val FIELD_FRAMING_FLAG_POS = 29
    }
}
