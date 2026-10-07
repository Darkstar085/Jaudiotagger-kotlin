package org.jaudiotagger.kt.audio.flac

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.InvalidTagDataException
import org.jaudiotagger.kt.io.int32BE
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.PictureTypes

/**
 * Codec for the FLAC PICTURE metadata block. The same binary layout is reused by
 * Ogg (base64-encoded in `METADATA_BLOCK_PICTURE`), so this lives apart from the
 * FLAC reader/writer. Maps 1:1 to the common [Artwork] type.
 */
object FlacPictureCodec {

    fun decode(rawData: ByteArray): Artwork {
        var pos = 0

        val pictureType = rawData.readInt32BE(pos)
        pos += 4
        if (pictureType >= PictureTypes.size) {
            throw InvalidTagDataException(
                "PictureType was:$pictureType but the maximum allowed is ${PictureTypes.size - 1}"
            )
        }

        val mimeTypeSize = rawData.readInt32BE(pos)
        pos += 4
        if (mimeTypeSize < 0 || mimeTypeSize > rawData.size - pos) {
            throw InvalidTagDataException("PictureType mimeType size was invalid:$mimeTypeSize")
        }
        val mimeType = rawData.decodeToString(pos, pos + mimeTypeSize)
        pos += mimeTypeSize

        val descriptionSize = rawData.readInt32BE(pos)
        pos += 4
        if (descriptionSize < 0 || descriptionSize > rawData.size - pos) {
            throw InvalidTagDataException("PictureType descriptionSize size was invalid:$descriptionSize")
        }
        val description = rawData.decodeToString(pos, pos + descriptionSize)
        pos += descriptionSize

        val width = rawData.readInt32BE(pos); pos += 4
        val height = rawData.readInt32BE(pos); pos += 4
        val colourDepth = rawData.readInt32BE(pos); pos += 4
        val indexedColourCount = rawData.readInt32BE(pos); pos += 4

        val dataLength = rawData.readInt32BE(pos)
        pos += 4
        if (dataLength < 0 || dataLength > rawData.size - pos) {
            throw InvalidTagDataException(
                "PictureType Size was:$dataLength but remaining bytes size ${rawData.size - pos}"
            )
        }
        val imageData = rawData.copyOfRange(pos, pos + dataLength)

        return Artwork(
            data = imageData,
            mimeType = mimeType,
            description = description,
            pictureType = pictureType,
            width = width,
            height = height,
            colourDepth = colourDepth,
            indexedColourCount = indexedColourCount,
        )
    }

    fun encode(artwork: Artwork): ByteArray {
        val buffer = Buffer()
        val mimeBytes = artwork.mimeType.encodeToByteArray()
        val descriptionBytes = artwork.description.encodeToByteArray()
        buffer.write(int32BE(artwork.pictureType))
        buffer.write(int32BE(mimeBytes.size))
        buffer.write(mimeBytes)
        buffer.write(int32BE(descriptionBytes.size))
        buffer.write(descriptionBytes)
        buffer.write(int32BE(artwork.width))
        buffer.write(int32BE(artwork.height))
        buffer.write(int32BE(artwork.colourDepth))
        buffer.write(int32BE(artwork.indexedColourCount))
        buffer.write(int32BE(artwork.data.size))
        buffer.write(artwork.data)
        return buffer.readByteArray()
    }
}
