package org.jaudiotagger.kt.audio.ogg

import org.jaudiotagger.kt.AudioProperties
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.decodeLatin1
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * Ogg container. Vorbis and Opus streams share file extensions, so the codec is taken from the
 * first packet of the stream.
 */
internal object OggFile {

    fun readProperties(io: FileIo): AudioProperties {
        return when (detectCodec(io)) {
            OggCodec.VORBIS -> OggInfoReader.read(io)
            OggCodec.OPUS -> OpusInfoReader.read(io)
        }
    }

    fun readTag(io: FileIo): VorbisCommentTag {
        return when (detectCodec(io)) {
            OggCodec.VORBIS -> OggVorbisTagReader.read(io)
            OggCodec.OPUS -> OggOpusTagReader.read(io)
        }
    }

    fun writeTag(io: FileIo, tag: VorbisCommentTag) {
        when (detectCodec(io)) {
            OggCodec.VORBIS -> OggVorbisTagWriter.write(io, tag)
            OggCodec.OPUS -> OggOpusTagWriter.write(io, tag)
        }
    }

    fun deleteTag(io: FileIo) {
        when (detectCodec(io)) {
            OggCodec.VORBIS -> OggVorbisTagWriter.delete(io)
            OggCodec.OPUS -> OggOpusTagWriter.delete(io)
        }
    }

    private fun detectCodec(io: FileIo): OggCodec {
        io.position = OggInfoReader.findFirstPageOffset(io)
        val firstPage = OggPageHeader.read(io)
        val packetStart =
            io.readFully(minOf(firstPage.pageLength, OpusHeader.CAPTURE_PATTERN_LENGTH))
        return when {
            VorbisHeader.isHeaderOfType(
                packetStart,
                VorbisPacketType.IDENTIFICATION_HEADER
            ) -> OggCodec.VORBIS

            packetStart.decodeLatin1() == OpusHeader.HEAD_CAPTURE_PATTERN -> OggCodec.OPUS
            else -> throw CannotReadException("Unsupported Ogg codec, only Vorbis and Opus can be read")
        }
    }

    private enum class OggCodec { VORBIS, OPUS }
}
