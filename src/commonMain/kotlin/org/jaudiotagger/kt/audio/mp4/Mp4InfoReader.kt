package org.jaudiotagger.kt.audio.mp4

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.io.readUInt16BE
import org.jaudiotagger.kt.io.u

/**
 * Reads MP4 audio properties from moov: duration from mvhd, sample rate and
 * channels from the stsd sample entry, bitrate from the esds decoder config
 * (with a size/duration estimate as fallback).
 */
internal object Mp4InfoReader {

    fun read(io: FileIo): AudioProperties {
        // quick brand sanity check
        val ftyp = Mp4Atoms.findChild(io, 0, io.size, "ftyp")
            ?: throw CannotReadException("This file does not appear to be an audio file")

        val moov = Mp4Atoms.requirePath(io, "moov")
        val mvhd = Mp4Atoms.requirePath(io, "moov", "mvhd")

        io.position = mvhd.dataStart
        val mvhdData = io.readFully(minOf(mvhd.dataLength, 32).toInt())
        val version = u(mvhdData[0])
        val timescale: Long
        val duration: Long
        if (version == 1) {
            timescale = mvhdData.readInt32BE(20).toUInt().toLong()
            duration = (mvhdData.readInt32BE(24).toUInt().toLong() shl 32) or
                mvhdData.readInt32BE(28).toUInt().toLong()
        } else {
            timescale = mvhdData.readInt32BE(12).toUInt().toLong()
            duration = mvhdData.readInt32BE(16).toUInt().toLong()
        }
        val durationSeconds = if (timescale > 0) duration.toDouble() / timescale else 0.0

        // first audio sample entry inside moov.trak.mdia.minf.stbl.stsd
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var encodingType = ""
        var isLossless = false
        var bitRate = 0

        val stsd = Mp4Atoms.findPath(io, "moov", "trak", "mdia", "minf", "stbl", "stsd")
        if (stsd != null) {
            Mp4Atoms.forEachChild(io, stsd.dataStart + 8, stsd.dataEnd) { entry ->
                if (channels != 0) return@forEachChild
                when (entry.id) {
                    "mp4a", "alac", "drms" -> {
                        io.position = entry.dataStart
                        val data = io.readFully(minOf(entry.dataLength, 28).toInt())
                        // 6 reserved + 2 data ref index + 8 reserved
                        channels = data.readUInt16BE(16)
                        bitsPerSample = data.readUInt16BE(18)
                        sampleRate = data.readUInt16BE(24) // 16.16 fixed point, integer part
                        encodingType = when (entry.id) {
                            "alac" -> "Apple Lossless"
                            "drms" -> "DRM AAC"
                            else -> "AAC"
                        }
                        isLossless = entry.id == "alac"
                        if (entry.id == "mp4a") {
                            val esds = Mp4Atoms.findChild(io, entry.dataStart + 28, entry.dataEnd, "esds")
                            if (esds != null) {
                                bitRate = readEsdsAvgBitrate(io, esds) / 1000
                            }
                        }
                    }
                }
            }
        }

        if (durationSeconds <= 0.0 && sampleRate <= 0) {
            throw CannotReadException("This file does not appear to be an audio file")
        }
        if (bitRate == 0 && durationSeconds > 0) {
            // estimate from the media data size
            val mdat = Mp4Atoms.findChild(io, 0, io.size, "mdat")
            if (mdat != null) {
                bitRate = (mdat.dataLength * 8 / durationSeconds / 1000).toInt()
            }
        }

        return AudioProperties(
            format = "MP4",
            encodingType = encodingType.ifEmpty { "AAC" },
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            bitRate = bitRate,
            isVariableBitRate = false,
            isLossless = isLossless,
            duration = durationSeconds.seconds,
            totalSamples = if (sampleRate > 0) (durationSeconds * sampleRate).toLong() else null,
        )
    }

    /**
     * Walks the esds MPEG-4 descriptor chain to the DecoderConfig descriptor
     * (tag 0x04) and returns its average bitrate in bits per second.
     */
    private fun readEsdsAvgBitrate(io: FileIo, esds: Mp4Atoms.AtomInfo): Int {
        io.position = esds.dataStart
        val data = io.readFully(esds.dataLength.toInt())
        var pos = 4 // version + flags

        fun readDescriptorHeader(): Pair<Int, Int>? {
            if (pos >= data.size) return null
            val tag = u(data[pos])
            pos++
            var size = 0
            var count = 0
            while (pos < data.size && count < 4) {
                val b = u(data[pos])
                pos++
                count++
                size = (size shl 7) or (b and 0x7F)
                if ((b and 0x80) == 0) break
            }
            return tag to size
        }

        while (true) {
            val (tag, _) = readDescriptorHeader() ?: return 0
            when (tag) {
                0x03 -> pos += 3 // ES id + stream priority flags, then children follow
                0x04 -> {
                    // objectType(1) streamType(1) bufferSize(3) maxBitrate(4) avgBitrate(4)
                    if (pos + 13 > data.size) return 0
                    return data.readInt32BE(pos + 9)
                }

                else -> return 0
            }
        }
    }
}
