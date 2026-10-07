package org.jaudiotagger.kt.mp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.audio.mp3.Mp3AudioProperties
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version

class Id3v2WriteTest {

    private fun audioLength(path: kotlinx.io.files.Path): Long {
        val p = AudioTagger.read(path).properties as Mp3AudioProperties
        return p.audioDataEndPosition - p.audioDataStartPosition
    }

    @Test
    fun rewriteInPlaceWithinExistingPadding() {
        val path = copyToTemp("issue52.mp3", "id3v2-inplace")
        val originalSize = SystemFileSystem.metadataOrNull(path)!!.size
        val originalAudio = audioLength(path)

        // issue52.mp3 has a 256KB v2.3 tag area, plenty of room
        val tag = AudioTagger.read(path).tag as Id3v2Tag
        tag.set(FieldKey.TITLE, "New Title")
        tag.set(FieldKey.MUSICBRAINZ_RELEASEID, "1234-5678")
        AudioTagger.write(path, tag)

        assertEquals(originalSize, SystemFileSystem.metadataOrNull(path)!!.size, "must reuse padding")
        val reread = AudioTagger.read(path)
        assertEquals("New Title", reread.tag.first(FieldKey.TITLE))
        assertEquals("Billy Talent", reread.tag.first(FieldKey.ARTIST))
        assertEquals("1234-5678", reread.tag.first(FieldKey.MUSICBRAINZ_RELEASEID))
        assertEquals(originalAudio, audioLength(path))

        SystemFileSystem.delete(path)
    }

    @Test
    fun growTagShiftsAudio() {
        val path = copyToTemp("testV1Cbr128.mp3", "id3v2-grow")
        val originalSize = SystemFileSystem.metadataOrNull(path)!!.size
        val originalAudio = audioLength(path)

        // no v2 tag initially: create one with artwork bigger than any padding
        val tag = AudioTagger.createTag(org.jaudiotagger.kt.AudioFormat.MP3) as Id3v2Tag
        tag.set(FieldKey.TITLE, "Tïtle 日本語")
        tag.set(FieldKey.ARTIST, "Artist")
        val imageBytes = ByteArray(50_000) { (it * 3).toByte() }
        tag.addArtwork(Artwork(data = imageBytes, mimeType = "image/jpeg", description = "front"))
        AudioTagger.write(path, tag)

        assertTrue(SystemFileSystem.metadataOrNull(path)!!.size > originalSize + 50_000)
        val reread = AudioTagger.read(path)
        val rereadTag = assertIs<Id3v2Tag>(reread.tag)
        assertEquals("Tïtle 日本語", rereadTag.first(FieldKey.TITLE))
        assertEquals("Artist", rereadTag.first(FieldKey.ARTIST))
        assertEquals(1, rereadTag.artworks.size)
        assertEquals("image/jpeg", rereadTag.artworks[0].mimeType)
        assertEquals("front", rereadTag.artworks[0].description)
        assertTrue(rereadTag.artworks[0].data.contentEquals(imageBytes))
        assertEquals(originalAudio, audioLength(path))

        // second write of a smaller tag reuses the created padding
        rereadTag.clearArtworks()
        val sizeBefore = SystemFileSystem.metadataOrNull(path)!!.size
        AudioTagger.write(path, rereadTag)
        assertEquals(sizeBefore, SystemFileSystem.metadataOrNull(path)!!.size)
        assertEquals(0, AudioTagger.read(path).tag.artworks.size)

        SystemFileSystem.delete(path)
    }

    @Test
    fun roundTripAllVersions() {
        for (sample in listOf("test51.mp3", "issue52.mp3", "test23.mp3")) {
            val path = copyToTemp(sample, "id3v2-roundtrip")
            val original = AudioTagger.read(path)
            val tag = assertIs<Id3v2Tag>(original.tag, sample)
            val version = tag.version
            val artistBefore = tag.first(FieldKey.ARTIST)

            tag.set(FieldKey.ALBUM, "Round Trip Album")
            AudioTagger.write(path, tag)

            val reread = assertIs<Id3v2Tag>(AudioTagger.read(path).tag, sample)
            assertEquals(version, reread.version, sample)
            assertEquals("Round Trip Album", reread.first(FieldKey.ALBUM), sample)
            assertEquals(artistBefore, reread.first(FieldKey.ARTIST), sample)

            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun deleteRemovesBothTagsAndKeepsAudio() {
        val path = copyToTemp("testV1Cbr128ID3v1v2.mp3", "id3v2-delete")
        val originalAudio = audioLength(path)

        AudioTagger.deleteTag(path)

        val reread = AudioTagger.read(path)
        assertEquals(null, reread.tag.first(FieldKey.TITLE))
        assertEquals(0, (reread.properties as Mp3AudioProperties).audioDataStartPosition)
        // "audio" previously included the trailing 128-byte ID3v1 block
        assertEquals(originalAudio - 128, audioLength(path))
        assertEquals(originalAudio - 128, SystemFileSystem.metadataOrNull(path)!!.size)

        SystemFileSystem.delete(path)
    }

    @Test
    fun unknownFramesSurviveRewrite() {
        val path = copyToTemp("issue52.mp3", "id3v2-unknown")
        val tag = AudioTagger.read(path).tag as Id3v2Tag
        // zero-length frames are invalid per spec and are dropped on write
        val unknownBefore = tag.frames
            .filterNot { it is org.jaudiotagger.kt.tag.id3.Id3v2Frame.Unknown && it.rawBody.isEmpty() }
            .map { it.id }.sorted()

        tag.set(FieldKey.TITLE, "Changed")
        AudioTagger.write(path, tag)

        val reread = AudioTagger.read(path).tag as Id3v2Tag
        // every frame id present before must survive (COMM/private frames included)
        val after = reread.frames.map { it.id }.sorted()
        assertEquals(unknownBefore, after)

        SystemFileSystem.delete(path)
    }

    @Test
    fun newTagDefaultsToV23() {
        val tag = AudioTagger.createTag(org.jaudiotagger.kt.AudioFormat.MP3)
        assertEquals(Id3v2Version.V23, (tag as Id3v2Tag).version)
    }
}
