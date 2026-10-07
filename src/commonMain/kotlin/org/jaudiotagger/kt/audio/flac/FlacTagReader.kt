package org.jaudiotagger.kt.audio.flac

import org.jaudiotagger.kt.AudioException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.tag.flac.FlacTag
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * Reads the FLAC tag: Vorbis Comment block plus PICTURE blocks.
 */
internal object FlacTagReader {

    fun read(io: FileIo): FlacTag {
        val flacStream = FlacStreamReader(io)
        flacStream.findStream()

        var vorbisComment: VorbisCommentTag? = null
        val tag = FlacTag()

        var isLastBlock = false
        while (!isLastBlock) {
            val header = MetadataBlockHeader.read(io)
            when (header.blockType) {
                BlockType.VORBIS_COMMENT -> {
                    val rawPacket = RawMetadataBlock.read(io, header).data
                    vorbisComment = VorbisCommentCodec.decode(rawPacket, hasFramingBit = false)
                }

                BlockType.PICTURE -> {
                    try {
                        val rawPicture = RawMetadataBlock.read(io, header).data
                        tag.addArtwork(FlacPictureCodec.decode(rawPicture))
                    } catch (_: AudioException) {
                        // unreadable picture block: ignore, the rest of the tag is fine
                    }
                }

                else -> io.position += header.dataLength
            }
            isLastBlock = header.isLastBlock
        }

        // No vorbis comment block is valid; expose an empty tag
        return if (vorbisComment == null) tag else FlacTag(vorbisComment).also { result ->
            tag.artworks.forEach(result::addArtwork)
        }
    }
}
