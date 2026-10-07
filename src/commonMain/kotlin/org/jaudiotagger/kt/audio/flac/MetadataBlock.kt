package org.jaudiotagger.kt.audio.flac

import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readUInt24BE
import org.jaudiotagger.kt.io.u

/**
 * FLAC metadata block types. Ordinal equals the on-disk id.
 */
enum class BlockType {
    STREAMINFO,
    PADDING,
    APPLICATION,
    SEEKTABLE,
    VORBIS_COMMENT,
    CUESHEET,
    PICTURE;

    val id: Int get() = ordinal
}

/**
 * 4-byte header preceding each FLAC metadata block:
 * 1 bit last-block flag, 7 bits block type, 24 bits data length (BE).
 */
class MetadataBlockHeader(
    val isLastBlock: Boolean,
    val blockType: BlockType,
    val dataLength: Int,
) {
    fun bytes(asLastBlock: Boolean): ByteArray {
        val typeByte = if (asLastBlock) (0x80 or blockType.id) else blockType.id
        return byteArrayOf(
            typeByte.toByte(),
            (dataLength ushr 16).toByte(),
            (dataLength ushr 8).toByte(),
            dataLength.toByte(),
        )
    }

    override fun toString(): String =
        "BlockType:$blockType DataLength:$dataLength isLastBlock:$isLastBlock"

    companion object {
        const val HEADER_LENGTH = 4

        fun read(io: FileIo): MetadataBlockHeader {
            val raw = io.readFully(HEADER_LENGTH)
            val isLast = (u(raw[0]) and 0x80) != 0
            val type = u(raw[0]) and 0x7F
            if (type >= BlockType.entries.size) {
                throw CannotReadException("Flac file has invalid block type $type")
            }
            return MetadataBlockHeader(isLast, BlockType.entries[type], raw.readUInt24BE(1))
        }
    }
}

/**
 * A metadata block whose content the library does not interpret
 * (APPLICATION, SEEKTABLE, CUESHEET): kept verbatim for rewrite.
 */
class RawMetadataBlock(
    val header: MetadataBlockHeader,
    val data: ByteArray,
) {
    /** Total on-disk size including the 4-byte header. */
    val length: Int get() = MetadataBlockHeader.HEADER_LENGTH + data.size

    companion object {
        fun read(io: FileIo, header: MetadataBlockHeader): RawMetadataBlock =
            RawMetadataBlock(header, io.readFully(header.dataLength))
    }
}
