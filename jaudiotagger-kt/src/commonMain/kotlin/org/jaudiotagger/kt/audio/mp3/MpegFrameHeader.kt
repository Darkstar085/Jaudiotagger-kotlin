package org.jaudiotagger.kt.audio.mp3

import org.jaudiotagger.kt.InvalidTagDataException
import org.jaudiotagger.kt.io.u

/**
 * A 4-byte MPEG audio frame header.
 * Ported from `MPEGFrameHeader`; lookup tables kept identical.
 */
internal class MpegFrameHeader private constructor(private val bytes: ByteArray) {

    val version: Int = (bytes[1].toInt() and MASK_VERSION) shr 3
    val layer: Int = (bytes[1].toInt() and MASK_LAYER) ushr 1
    val channelMode: Int = (u(bytes[3]) and MASK_MODE) ushr 6
    val emphasis: Int = bytes[3].toInt() and MASK_EMPHASIS
    val isPadding: Boolean = (bytes[2].toInt() and MASK_PADDING) != 0
    val isProtected: Boolean = (bytes[1].toInt() and MASK_PROTECTION) == 0
    val isCopyrighted: Boolean = (bytes[3].toInt() and MASK_COPY) != 0
    val isOriginal: Boolean = (bytes[3].toInt() and MASK_HOME) != 0

    val bitRate: Int
    val samplingRate: Int

    init {
        val bitRateIndex = (u(bytes[2]) and MASK_BITRATE) or
            (bytes[1].toInt() and MASK_ID) or (bytes[1].toInt() and MASK_LAYER)
        bitRate = bitrateMap[bitRateIndex] ?: throw InvalidTagDataException("Invalid bitrate")

        if (version !in versionNames) throw InvalidTagDataException("Invalid mpeg version")
        if (layer !in layerNames) throw InvalidTagDataException("Invalid Layer")
        if (channelMode > MODE_MONO) throw InvalidTagDataException("Invalid channel mode")

        val samplingIndex = (bytes[2].toInt() and MASK_FREQUENCY) ushr 2
        samplingRate = samplingRateMap[version]?.get(samplingIndex)
            ?: throw InvalidTagDataException("Invalid sampling rate")
    }

    val versionAsString: String get() = versionNames.getValue(version)
    val layerAsString: String get() = layerNames.getValue(layer)

    val numberOfChannels: Int get() = if (channelMode == MODE_MONO) 1 else 2

    /** Samples per frame; ported from [org.jaudiotagger.audio.mp3.MPEGFrameHeader.getNoOfSamples]. */
    val noOfSamples: Int
        get() = samplesPerFrameMap[version]?.get(layer)
            ?: throw InvalidTagDataException("Mp3 unknown version/layer: $version/$layer")

    /**
     * Slot length in bytes for this frame header (ISO/IEC 13818-3).
     * MPEG-2/2.5 Layer III always uses 72 × bitrate / sampleRate + padding.
     */
    val frameLength: Int
        get() {
            val paddingLength = if (isPadding) 1 else 0
            return when (version) {
                VERSION_2, VERSION_2_5 -> when (layer) {
                    LAYER_I -> (12 * (bitRate * 1000) / samplingRate + paddingLength) * 4
                    LAYER_II -> 144 * (bitRate * 1000) / samplingRate + paddingLength
                    LAYER_III -> 72 * (bitRate * 1000) / samplingRate + paddingLength

                    else -> throw InvalidTagDataException("Mp3 Unknown Layer:$layer")
                }

                VERSION_1 -> when (layer) {
                    LAYER_I -> (12 * (bitRate * 1000) / samplingRate + paddingLength) * 4
                    else -> 144 * (bitRate * 1000) / samplingRate + paddingLength
                }

                else -> throw InvalidTagDataException("Mp3 Unknown Version:$version")
            }
        }

    companion object {
        const val HEADER_SIZE = 4

        const val VERSION_2_5 = 0
        const val VERSION_2 = 2
        const val VERSION_1 = 3

        const val LAYER_I = 3
        const val LAYER_II = 2
        const val LAYER_III = 1

        const val MODE_STEREO = 0
        const val MODE_JOINT_STEREO = 1
        const val MODE_DUAL_CHANNEL = 2
        const val MODE_MONO = 3

        private const val MASK_ID = 0x08
        private const val MASK_VERSION = 0x18
        private const val MASK_LAYER = 0x06
        private const val MASK_PROTECTION = 0x01
        private const val MASK_BITRATE = 0xF0
        private const val MASK_FREQUENCY = 0x0C
        private const val MASK_PADDING = 0x02
        private const val MASK_MODE = 0xC0
        private const val MASK_COPY = 0x08
        private const val MASK_HOME = 0x04
        private const val MASK_EMPHASIS = 0x03

        private val versionNames = mapOf(
            VERSION_2_5 to "MPEG-2.5",
            VERSION_2 to "MPEG-2",
            VERSION_1 to "MPEG-1",
        )

        private val layerNames = mapOf(
            LAYER_I to "Layer 1",
            LAYER_II to "Layer 2",
            LAYER_III to "Layer 3",
        )

        /** Keyed by bitrate nibble | version id bit | layer bits, as in the original. */
        private val bitrateMap: Map<Int, Int> = buildMap {
            // MPEG-1, Layer I (E)
            put(0x1E, 32); put(0x2E, 64); put(0x3E, 96); put(0x4E, 128); put(0x5E, 160)
            put(0x6E, 192); put(0x7E, 224); put(0x8E, 256); put(0x9E, 288); put(0xAE, 320)
            put(0xBE, 352); put(0xCE, 384); put(0xDE, 416); put(0xEE, 448)
            // MPEG-1, Layer II (C)
            put(0x1C, 32); put(0x2C, 48); put(0x3C, 56); put(0x4C, 64); put(0x5C, 80)
            put(0x6C, 96); put(0x7C, 112); put(0x8C, 128); put(0x9C, 160); put(0xAC, 192)
            put(0xBC, 224); put(0xCC, 256); put(0xDC, 320); put(0xEC, 384)
            // MPEG-1, Layer III (A)
            put(0x1A, 32); put(0x2A, 40); put(0x3A, 48); put(0x4A, 56); put(0x5A, 64)
            put(0x6A, 80); put(0x7A, 96); put(0x8A, 112); put(0x9A, 128); put(0xAA, 160)
            put(0xBA, 192); put(0xCA, 224); put(0xDA, 256); put(0xEA, 320)
            // MPEG-2, Layer I (6)
            put(0x16, 32); put(0x26, 48); put(0x36, 56); put(0x46, 64); put(0x56, 80)
            put(0x66, 96); put(0x76, 112); put(0x86, 128); put(0x96, 144); put(0xA6, 160)
            put(0xB6, 176); put(0xC6, 192); put(0xD6, 224); put(0xE6, 256)
            // MPEG-2, Layer II (4)
            put(0x14, 8); put(0x24, 16); put(0x34, 24); put(0x44, 32); put(0x54, 40)
            put(0x64, 48); put(0x74, 56); put(0x84, 64); put(0x94, 80); put(0xA4, 96)
            put(0xB4, 112); put(0xC4, 128); put(0xD4, 144); put(0xE4, 160)
            // MPEG-2, Layer III (2)
            put(0x12, 8); put(0x22, 16); put(0x32, 24); put(0x42, 32); put(0x52, 40)
            put(0x62, 48); put(0x72, 56); put(0x82, 64); put(0x92, 80); put(0xA2, 96)
            put(0xB2, 112); put(0xC2, 128); put(0xD2, 144); put(0xE2, 160)
        }

        private val samplingRateMap: Map<Int, Map<Int, Int>> = mapOf(
            VERSION_1 to mapOf(0 to 44100, 1 to 48000, 2 to 32000),
            VERSION_2 to mapOf(0 to 22050, 1 to 24000, 2 to 16000),
            VERSION_2_5 to mapOf(0 to 11025, 1 to 12000, 2 to 8000),
        )

        /** MPEG-2/2.5 Layer III uses 576 samples per frame (half of MPEG-1 Layer III). */
        private val samplesPerFrameMap: Map<Int, Map<Int, Int>> = mapOf(
            VERSION_1 to mapOf(LAYER_I to 384, LAYER_II to 1152, LAYER_III to 1152),
            VERSION_2 to mapOf(LAYER_I to 384, LAYER_II to 1152, LAYER_III to 576),
            VERSION_2_5 to mapOf(LAYER_I to 384, LAYER_II to 1152, LAYER_III to 576),
        )

        /** Quick sync check: 11 set bits and a valid sampling-rate field. */
        fun isMpegFrame(buffer: ByteArray, offset: Int): Boolean =
            (u(buffer[offset]) and 0xFF) == 0xFF &&
                (u(buffer[offset + 1]) and 0xE0) == 0xE0 &&
                (u(buffer[offset + 2]) and 0xFC) != 0xFC

        fun parse(buffer: ByteArray, offset: Int): MpegFrameHeader =
            MpegFrameHeader(buffer.copyOfRange(offset, offset + HEADER_SIZE))
    }
}
