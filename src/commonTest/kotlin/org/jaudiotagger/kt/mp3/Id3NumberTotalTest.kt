package org.jaudiotagger.kt.mp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v2Frame
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version
import org.jaudiotagger.kt.tag.id3.PartOfSetValue

class Id3NumberTotalTest {

    @Test
    fun readSplitTrackAndDiscNumbers() {
        val path = copyToTemp("issue52.mp3", "trck-read")
        val tag = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
        tag.frames.removeAll { it.id == "TRCK" || it.id == "TPOS" }
        tag.frames += Id3v2Frame.NumberTotal("TRCK", PartOfSetValue("3/12"))
        tag.frames += Id3v2Frame.NumberTotal("TPOS", PartOfSetValue("1/2"))
        AudioTagger.write(path, tag)

        val reread = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
        assertEquals("3", reread.first(FieldKey.TRACK))
        assertEquals("12", reread.first(FieldKey.TRACK_TOTAL))
        assertEquals("1", reread.first(FieldKey.DISC_NO))
        assertEquals("2", reread.first(FieldKey.DISC_TOTAL))
        SystemFileSystem.delete(path)
    }

    @Test
    fun writePreservesTotalWhenSettingTrackAlone() {
        for (sample in listOf("issue52.mp3" to Id3v2Version.V23, "test23.mp3" to Id3v2Version.V24)) {
            val path = copyToTemp(sample.first, "trck-write-${sample.second.name}")
            val tag = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
            tag.set(FieldKey.TRACK, "3")
            tag.set(FieldKey.TRACK_TOTAL, "12")
            tag.set(FieldKey.DISC_NO, "1")
            tag.set(FieldKey.DISC_TOTAL, "2")
            AudioTagger.write(path, tag)

            val reread = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
            assertEquals("3", reread.first(FieldKey.TRACK), sample.first)
            assertEquals("12", reread.first(FieldKey.TRACK_TOTAL), sample.first)
            assertEquals("1", reread.first(FieldKey.DISC_NO), sample.first)
            assertEquals("2", reread.first(FieldKey.DISC_TOTAL), sample.first)

            reread.set(FieldKey.TRACK, "5")
            AudioTagger.write(path, reread)
            val afterTrack = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
            assertEquals("5", afterTrack.first(FieldKey.TRACK), sample.first)
            assertEquals("12", afterTrack.first(FieldKey.TRACK_TOTAL), sample.first)

            afterTrack.remove(FieldKey.TRACK)
            AudioTagger.write(path, afterTrack)
            val afterRemove = assertIs<Id3v2Tag>(AudioTagger.read(path).tag)
            assertNull(afterRemove.first(FieldKey.TRACK), sample.first)
            assertEquals("12", afterRemove.first(FieldKey.TRACK_TOTAL), sample.first)
            assertIs<Id3v2Frame.NumberTotal>(
                afterRemove.frames.first { it.id == "TRCK" },
                sample.first,
            )

            SystemFileSystem.delete(path)
        }
    }
}
