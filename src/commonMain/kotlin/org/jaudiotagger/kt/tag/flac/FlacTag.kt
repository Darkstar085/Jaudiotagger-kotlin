package org.jaudiotagger.kt.tag.flac

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

/**
 * FLAC tag: a Vorbis Comment block for text fields plus PICTURE metadata blocks
 * for artwork.
 */
class FlacTag(
    val vorbisComment: VorbisCommentTag = VorbisCommentTag(),
) : Tag {

    private val pictures = mutableListOf<Artwork>()

    override fun first(key: FieldKey): String? = vorbisComment.first(key)

    override fun all(key: FieldKey): List<String> = vorbisComment.all(key)

    override fun set(key: FieldKey, value: String) = vorbisComment.set(key, value)

    override fun add(key: FieldKey, value: String) = vorbisComment.add(key, value)

    override fun remove(key: FieldKey) = vorbisComment.remove(key)

    override val fieldCount: Int get() = vorbisComment.fieldCount + pictures.size

    override val isEmpty: Boolean get() = vorbisComment.isEmpty && pictures.isEmpty()

    override fun clear() {
        vorbisComment.clear()
        pictures.clear()
    }

    override val artworks: List<Artwork> get() = pictures

    override fun addArtwork(artwork: Artwork) {
        pictures += artwork
    }

    override fun clearArtworks() {
        pictures.clear()
    }

    override fun toString(): String = "FlacTag($vorbisComment, pictures=${pictures.size})"
}
