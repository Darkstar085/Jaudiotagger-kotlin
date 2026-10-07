package org.jaudiotagger.kt.audio.flac

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.ShiftData
import org.jaudiotagger.kt.tag.flac.FlacTag
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentCodec

/**
 * Writes a FLAC tag in place.
 *
 * Blocks are written in the order STREAMINFO, VORBIS_COMMENT, PICTURE*, SEEKTABLE,
 * CUESHEET, APPLICATION*, PADDING for best compatibility with other applications.
 * When the new metadata fits into the existing metadata area the audio is left
 * untouched; otherwise the audio is shifted in chunks to make room.
 */
internal object FlacTagWriter {

    const val DEFAULT_PADDING = 4000

    /** Removes the Vorbis Comment and all pictures, keeping other blocks. */
    fun delete(io: FileIo) {
        write(io, null)
    }

    /** Writes [tag]; null deletes the tag blocks entirely. */
    fun write(io: FileIo, tag: FlacTag?) {
        val flacStream = FlacStreamReader(io)
        try {
            flacStream.findStream()
        } catch (e: CannotReadException) {
            throw CannotWriteException(e.message, e)
        }

        var streamInfoBlock: RawMetadataBlock? = null
        // VORBIS_COMMENT, PADDING and PICTURE blocks will be replaced by the new tag;
        // only their combined size matters
        var replaceableRoom = 0
        val seekTableBlocks = mutableListOf<RawMetadataBlock>()
        val cueSheetBlocks = mutableListOf<RawMetadataBlock>()
        val applicationBlocks = mutableListOf<RawMetadataBlock>()

        var isLastBlock = false
        while (!isLastBlock) {
            val header = try {
                MetadataBlockHeader.read(io)
            } catch (e: CannotReadException) {
                throw CannotWriteException(e.message, e)
            }
            when (header.blockType) {
                BlockType.STREAMINFO -> streamInfoBlock = RawMetadataBlock.read(io, header)

                BlockType.VORBIS_COMMENT, BlockType.PADDING, BlockType.PICTURE -> {
                    io.position += header.dataLength
                    replaceableRoom += MetadataBlockHeader.HEADER_LENGTH + header.dataLength
                }

                BlockType.APPLICATION -> applicationBlocks += RawMetadataBlock.read(io, header)
                BlockType.SEEKTABLE -> seekTableBlocks += RawMetadataBlock.read(io, header)
                BlockType.CUESHEET -> cueSheetBlocks += RawMetadataBlock.read(io, header)
            }
            isLastBlock = header.isLastBlock
        }

        if (streamInfoBlock == null) {
            throw CannotWriteException("Unable to find Flac StreamInfo block")
        }

        // Blocks that are kept verbatim, in write order
        val otherBlocks = seekTableBlocks + cueSheetBlocks + applicationBlocks
        val otherBlocksSize = otherBlocks.sumOf { it.length }

        val availableRoom = replaceableRoom + otherBlocksSize
        val newTagSize = encodeTag(tag, blocksFollow = false).size
        val neededRoom = newTagSize + otherBlocksSize

        if (availableRoom == neededRoom || availableRoom > neededRoom + MetadataBlockHeader.HEADER_LENGTH) {
            // Fits: rewrite metadata in place, absorbing spare space into padding
            writeAllNonAudioData(
                io, tag, streamInfoBlock, otherBlocks, flacStream.startOfFlacInFile,
                padding = availableRoom - neededRoom,
            )
        } else {
            // Does not fit: shift audio to make room, then write with default padding
            val audioStart = flacStream.startOfFlacInFile +
                FlacStreamReader.FLAC_STREAM_IDENTIFIER_LENGTH +
                MetadataBlockHeader.HEADER_LENGTH +
                FlacStreamInfo.STREAM_INFO_DATA_LENGTH +
                availableRoom
            val extraSpaceRequired = neededRoom + DEFAULT_PADDING - availableRoom

            io.position = audioStart
            ShiftData.shiftDataByOffsetToMakeSpace(io, extraSpaceRequired)

            writeAllNonAudioData(
                io, tag, streamInfoBlock, otherBlocks, flacStream.startOfFlacInFile,
                padding = DEFAULT_PADDING,
            )
        }
        io.flush()
    }

    private fun writeAllNonAudioData(
        io: FileIo,
        tag: FlacTag?,
        streamInfoBlock: RawMetadataBlock,
        otherBlocks: List<RawMetadataBlock>,
        startOfFlacInFile: Long,
        padding: Int,
    ) {
        // Jump over ID3 (if any) and the fLaC marker
        io.position = startOfFlacInFile + FlacStreamReader.FLAC_STREAM_IDENTIFIER_LENGTH

        // STREAMINFO always goes first and is never the last block here
        io.write(streamInfoBlock.header.bytes(asLastBlock = false))
        io.write(streamInfoBlock.data)

        io.write(encodeTag(tag, blocksFollow = padding > 0 || otherBlocks.isNotEmpty()))

        for ((index, block) in otherBlocks.withIndex()) {
            val isLast = padding == 0 && index == otherBlocks.lastIndex
            io.write(block.header.bytes(asLastBlock = isLast))
            io.write(block.data)
        }

        if (padding > 0) {
            val paddingDataSize = padding - MetadataBlockHeader.HEADER_LENGTH
            val header = MetadataBlockHeader(true, BlockType.PADDING, paddingDataSize)
            io.write(header.bytes(asLastBlock = true))
            io.write(ByteArray(paddingDataSize))
        }
    }

    /**
     * Serializes the VORBIS_COMMENT and PICTURE blocks with headers.
     * [blocksFollow] controls the last-block flag of the final block produced here.
     */
    private fun encodeTag(tag: FlacTag?, blocksFollow: Boolean): ByteArray {
        if (tag == null) return ByteArray(0)

        val buffer = Buffer()

        val vorbisData = VorbisCommentCodec.encode(tag.vorbisComment)
        val pictures = tag.artworks
        val vorbisIsLast = !blocksFollow && pictures.isEmpty()
        buffer.write(MetadataBlockHeader(vorbisIsLast, BlockType.VORBIS_COMMENT, vorbisData.size).bytes(vorbisIsLast))
        buffer.write(vorbisData)

        for ((index, picture) in pictures.withIndex()) {
            val pictureData = FlacPictureCodec.encode(picture)
            val isLast = !blocksFollow && index == pictures.lastIndex
            buffer.write(MetadataBlockHeader(isLast, BlockType.PICTURE, pictureData.size).bytes(isLast))
            buffer.write(pictureData)
        }
        return buffer.readByteArray()
    }
}
