package org.jaudiotagger.kt

import kotlin.time.Duration

/**
 * Technical properties of the audio stream.
 */
open class AudioProperties(
    /** File format, e.g. "FLAC". */
    val format: String,
    /** Human readable encoding description, e.g. "FLAC 16 bits". */
    val encodingType: String,
    /** Sample rate in Hz. */
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    /** Average bitrate in kbit/s. */
    val bitRate: Int,
    val isVariableBitRate: Boolean = false,
    val isLossless: Boolean,
    val duration: Duration,
    /** Total inter-channel samples, when the format provides it. */
    val totalSamples: Long? = null,
    /** Offset of the first byte of audio data within the file. */
    val audioDataStartPosition: Long = -1,
    /** Offset one past the last byte of audio data. */
    val audioDataEndPosition: Long = -1,
) {
    val audioDataLength: Long
        get() = if (audioDataStartPosition >= 0 && audioDataEndPosition >= 0) {
            audioDataEndPosition - audioDataStartPosition
        } else -1

    override fun toString(): String =
        "$encodingType ${sampleRate}Hz ${channels}ch ${bitRate}kbps $duration"
}
