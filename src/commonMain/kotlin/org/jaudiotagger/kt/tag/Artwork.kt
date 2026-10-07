package org.jaudiotagger.kt.tag

/**
 * Cover art or other picture attached to an audio file.
 *
 * Platform-independent: holds the raw encoded image bytes; no image decoding
 * happens inside the library. [pictureType] follows the ID3v2 APIC picture type
 * list (see [PictureTypes]), which FLAC and Vorbis also use.
 *
 * When [isLinked] is true the artwork is an external reference: [linkedUrl]
 * holds the URL and [data] is its raw bytes.
 */
class Artwork(
    val data: ByteArray,
    val mimeType: String,
    val description: String = "",
    val pictureType: Int = PictureTypes.DEFAULT_ID,
    val width: Int = 0,
    val height: Int = 0,
    val colourDepth: Int = 0,
    val indexedColourCount: Int = 0,
) {
    val isLinked: Boolean get() = mimeType == LINKED_MIME_TYPE

    val linkedUrl: String get() = if (isLinked) data.decodeToString() else ""

    companion object {
        /** Pseudo mime type signifying the data is a URL of the picture, not the picture itself. */
        const val LINKED_MIME_TYPE = "-->"

        fun linked(url: String, pictureType: Int = PictureTypes.DEFAULT_ID): Artwork =
            Artwork(
                data = url.encodeToByteArray(),
                mimeType = LINKED_MIME_TYPE,
                pictureType = pictureType
            )
    }
}
