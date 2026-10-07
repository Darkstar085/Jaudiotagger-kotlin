package org.jaudiotagger.kt.audio.mp3

import org.jaudiotagger.kt.AudioProperties
import kotlin.time.Duration

class Mp3AudioProperties(
    encodingType: String,
    sampleRate: Int,
    channels: Int,
    bitRate: Int,
    isVariableBitRate: Boolean,
    duration: Duration,
    audioDataStartPosition: Long,
    audioDataEndPosition: Long,
    /** Number of MPEG frames (from Xing/VBRI when present, else estimated). */
    val numberOfFrames: Long,
    /** Encoder signature (e.g. "LAME3.99r"), empty when unknown. */
    val encoder: String,
    /** Channel mode string: Stereo, Joint Stereo, Dual, Mono. */
    val channelMode: String,
) : AudioProperties(
    format = "MP3",
    encodingType = encodingType,
    sampleRate = sampleRate,
    channels = channels,
    bitsPerSample = 16,
    bitRate = bitRate,
    isVariableBitRate = isVariableBitRate,
    isLossless = false,
    duration = duration,
    totalSamples = numberOfFrames,
    audioDataStartPosition = audioDataStartPosition,
    audioDataEndPosition = audioDataEndPosition,
)
