package org.jaudiotagger.kt.audio.flac

import org.jaudiotagger.kt.AudioProperties
import kotlin.time.Duration

class FlacAudioProperties(
    encodingType: String,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    bitRate: Int,
    duration: Duration,
    totalSamples: Long,
    audioDataStartPosition: Long,
    audioDataEndPosition: Long,
    /** MD5 of the unencoded audio data, hex encoded. */
    val md5: String,
) : AudioProperties(
    format = "FLAC",
    encodingType = encodingType,
    sampleRate = sampleRate,
    channels = channels,
    bitsPerSample = bitsPerSample,
    bitRate = bitRate,
    isLossless = true,
    duration = duration,
    totalSamples = totalSamples,
    audioDataStartPosition = audioDataStartPosition,
    audioDataEndPosition = audioDataEndPosition,
)
