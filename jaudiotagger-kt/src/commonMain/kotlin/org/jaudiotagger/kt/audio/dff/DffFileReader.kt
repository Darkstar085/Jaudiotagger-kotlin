package org.jaudiotagger.kt.audio.dff

import kotlin.time.Duration.Companion.seconds
import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.io.readUInt16BE
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2TagReader

/**
 * DFF (DSDIFF): big-endian IFF-style chunks inside a FRM8 form.
 * Read-only, like the original; unlike it, the trailing "ID3 " chunk is
 * exposed as a tag instead of being ignored.
 */
internal object DffFileReader {

    private class Layout(
        val sampleRate: Int,
        val channels: Int,
        val dsdDataLength: Long,
        val dstFrames: Int,
        val dstRate: Int,
        val id3Offset: Long,
    )

    private fun scan(io: FileIo): Layout {
        io.position = 0
        if (io.size < 16) throw CannotReadException("Not a valid dff file")
        val form = io.readFully(16)
        if (form.decodeToString(0, 4) != "FRM8" || form.decodeToString(12, 16) != "DSD ") {
            throw CannotReadException("Not a valid dff file. Content does not start with 'FRM8'")
        }

        var sampleRate = 0
        var channels = 0
        var dsdDataLength = -1L
        var dstFrames = 0
        var dstRate = 0
        var id3Offset = -1L

        while (io.position + 12 <= io.size) {
            val header = io.readFully(12)
            val id = header.decodeToString(0, 4)
            val size = (header.readInt32BE(4).toUInt().toLong() shl 32) or
                header.readInt32BE(8).toUInt().toLong()
            val dataStart = io.position

            when (id) {
                // containers: descend past their form-type / first sub-chunk
                "PROP" -> {
                    io.readFully(4) // "SND "
                    continue
                }

                "DST " -> continue // FRTE follows immediately

                "FS  " -> sampleRate = io.readFully(4).readInt32BE(0)

                "CHNL" -> channels = io.readFully(2).readUInt16BE(0)

                "FRTE" -> {
                    val data = io.readFully(6)
                    dstFrames = data.readInt32BE(0)
                    dstRate = data.readUInt16BE(4)
                }

                "DSD " -> dsdDataLength = size

                "ID3 " -> id3Offset = dataStart
            }
            val next = dataStart + size
            if (next <= dataStart || next > io.size) break
            io.position = next
        }

        if (channels == 0) throw CannotReadException("Not a valid dff file. Missing 'CHNL' chunk")
        if (sampleRate == 0) throw CannotReadException("Not a valid dff file. Missing 'FS' chunk")
        if (dsdDataLength < 0 && dstFrames == 0) {
            throw CannotReadException("Not a valid dff file. Missing 'DSD' data chunk")
        }
        return Layout(sampleRate, channels, dsdDataLength, dstFrames, dstRate, id3Offset)
    }

    fun readProperties(io: FileIo): AudioProperties {
        val layout = scan(io)
        val isDst = layout.dstFrames > 0
        val sampleCount: Long = if (isDst) {
            layout.dstFrames.toLong() / layout.dstRate * layout.sampleRate
        } else {
            layout.dsdDataLength * (8 / layout.channels)
        }

        return AudioProperties(
            format = "DFF",
            encodingType = "DFF",
            sampleRate = layout.sampleRate,
            channels = layout.channels,
            bitsPerSample = 1,
            bitRate = layout.sampleRate * layout.channels / 1000,
            isVariableBitRate = isDst,
            isLossless = true,
            duration = (sampleCount.toDouble() / layout.sampleRate).seconds,
            totalSamples = sampleCount,
        )
    }

    fun readTag(io: FileIo): Id3v2Tag? {
        val layout = scan(io)
        if (layout.id3Offset < 0) return null
        return Id3v2TagReader.read(io, layout.id3Offset)
    }
}
