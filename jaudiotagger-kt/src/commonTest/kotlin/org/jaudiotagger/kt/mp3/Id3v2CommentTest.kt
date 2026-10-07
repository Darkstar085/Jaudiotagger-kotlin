package org.jaudiotagger.kt.mp3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.id3.Id3v2Frame
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version

class Id3v2CommentTest {

    @Test
    fun emptyDescriptionCommentBeatsDescribed() {
        val tag = Id3v2Tag(Id3v2Version.V24)
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "Comment", "described")
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "", "empty wins")
        assertEquals("empty wins", tag.first(FieldKey.COMMENT))
        assertEquals(listOf("empty wins", "described"), tag.all(FieldKey.COMMENT))
    }

    @Test
    fun describedCommentIsFallback() {
        val tag = Id3v2Tag(Id3v2Version.V24)
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "cc", "some comment here")
        assertEquals("some comment here", tag.first(FieldKey.COMMENT))
    }

    @Test
    fun iTunesNormOnlyReadsNullComment() {
        val tag = Id3v2Tag(Id3v2Version.V24)
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "iTunNORM", "00000000 00000200")
        assertNull(tag.first(FieldKey.COMMENT))
    }

    @Test
    fun setCommentKeepsITunesFrame() {
        val tag = Id3v2Tag(Id3v2Version.V24)
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "iTunNORM", "00000000")
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "Comment", "old")
        tag.set(FieldKey.COMMENT, "new")
        val comments = tag.frames.filterIsInstance<Id3v2Frame.LanguageText>()
            .filter { it.id == "COMM" }
        assertEquals(2, comments.size)
        assertEquals(1, comments.count { it.description.startsWith("iTun", ignoreCase = true) })
        assertEquals("new", tag.first(FieldKey.COMMENT))
    }

    @Test
    fun removeCommentKeepsITunesFrame() {
        val tag = Id3v2Tag(Id3v2Version.V24)
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "iTunNORM", "00000000")
        tag.frames += Id3v2Frame.LanguageText("COMM", "eng", "", "user")
        tag.remove(FieldKey.COMMENT)
        assertNull(tag.first(FieldKey.COMMENT))
        assertEquals(1, tag.frames.filterIsInstance<Id3v2Frame.LanguageText>().size)
    }

    @Test
    fun emptyDescriptionLyricsBeatsDescribed() {
        val tag = Id3v2Tag(Id3v2Version.V24)
        tag.frames += Id3v2Frame.LanguageText("USLT", "eng", "desc", "described")
        tag.frames += Id3v2Frame.LanguageText("USLT", "eng", "", "empty wins")
        assertEquals("empty wins", tag.first(FieldKey.LYRICS))
    }
}
