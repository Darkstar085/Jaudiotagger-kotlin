package org.jaudiotagger.kt.ogg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

class OggWriteTest {

    @Test
    fun writeFieldsInPlace() {
        val path = copyToTemp("test.ogg", "ogg-write")
        val original = AudioTagger.read(path)
        val originalSamples = original.properties.totalSamples

        val tag = original.tag as VorbisCommentTag
        tag.set(FieldKey.ARTIST, "New Artist")
        tag.set(FieldKey.TITLE, "Tïtle 日本語")
        AudioTagger.write(path, tag)

        validateOggPageStructure(path)
        val reread = AudioTagger.read(path)
        assertEquals("New Artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals("Tïtle 日本語", reread.tag.first(FieldKey.TITLE))
        assertEquals("ddd", reread.tag.first(FieldKey.ALBUM))
        assertEquals(originalSamples, reread.properties.totalSamples)

        SystemFileSystem.delete(path)
    }

    @Test
    fun writeLargeCommentSpreadsOverMultiplePagesAndBack() {
        val path = copyToTemp("test.ogg", "ogg-grow")
        val originalPageCount = validateOggPageStructure(path)
        val original = AudioTagger.read(path)
        val originalSamples = original.properties.totalSamples

        // force the comment header over multiple ogg pages (page data max is 65025)
        val tag = original.tag as VorbisCommentTag
        tag.set(FieldKey.LYRICS, "y".repeat(150_000))
        AudioTagger.write(path, tag)

        val grownPageCount = validateOggPageStructure(path)
        assertTrue(grownPageCount > originalPageCount, "comment should now span extra pages")

        val reread = AudioTagger.read(path)
        assertEquals(150_000, reread.tag.first(FieldKey.LYRICS)!!.length)
        assertEquals("artist", reread.tag.first(FieldKey.ARTIST))
        assertEquals(originalSamples, reread.properties.totalSamples)

        // now shrink back to a small tag: pages collapse again, file stays valid
        val smallTag = reread.tag as VorbisCommentTag
        smallTag.remove(FieldKey.LYRICS)
        AudioTagger.write(path, smallTag)

        assertEquals(originalPageCount, validateOggPageStructure(path))
        val rereadSmall = AudioTagger.read(path)
        assertEquals(null, rereadSmall.tag.first(FieldKey.LYRICS))
        assertEquals("artist", rereadSmall.tag.first(FieldKey.ARTIST))
        assertEquals(originalSamples, rereadSmall.properties.totalSamples)

        SystemFileSystem.delete(path)
    }

    @Test
    fun writeArtwork() {
        val path = copyToTemp("test.ogg", "ogg-artwork")
        val file = AudioTagger.read(path)

        val imageBytes = ByteArray(4321) { (it * 7).toByte() }
        val tag = file.tag as VorbisCommentTag
        tag.setArtwork(Artwork(data = imageBytes, mimeType = "image/png", description = "cover"))
        AudioTagger.write(path, tag)

        validateOggPageStructure(path)
        val reread = AudioTagger.read(path)
        assertEquals(1, reread.tag.artworks.size)
        val artwork = reread.tag.artworks[0]
        assertEquals("image/png", artwork.mimeType)
        assertEquals("cover", artwork.description)
        assertTrue(artwork.data.contentEquals(imageBytes))

        SystemFileSystem.delete(path)
    }

    @Test
    fun deleteTag() {
        val path = copyToTemp("test.ogg", "ogg-delete")
        val originalSamples = AudioTagger.read(path).properties.totalSamples

        AudioTagger.deleteTag(path)

        validateOggPageStructure(path)
        val reread = AudioTagger.read(path)
        assertEquals(null, reread.tag.first(FieldKey.ARTIST))
        assertEquals(null, reread.tag.first(FieldKey.ALBUM))
        assertEquals(originalSamples, reread.properties.totalSamples)

        SystemFileSystem.delete(path)
    }
}
