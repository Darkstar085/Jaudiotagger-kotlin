package org.jaudiotagger.kt.tag.id3

import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.u

/**
 * Reads/writes the 128-byte ID3v1(.1) block at the end of a file:
 * "TAG", title[30], artist[30], album[30], year[4], comment[30], genre[1].
 * In v1.1 comment[28] is zero and comment[29] holds the track number.
 */
internal object Id3v1TagIo {

    const val TAG_LENGTH = 128

    fun findTagStart(io: FileIo): Long? {
        if (io.size < TAG_LENGTH) return null
        val start = io.size - TAG_LENGTH
        io.position = start
        val id = io.readFully(3)
        return if (id[0] == 'T'.code.toByte() && id[1] == 'A'.code.toByte() && id[2] == 'G'.code.toByte()) {
            start
        } else null
    }

    fun read(io: FileIo): Id3v1Tag? {
        val start = findTagStart(io) ?: return null
        io.position = start + 3
        val data = io.readFully(TAG_LENGTH - 3)

        val tag = Id3v1Tag()
        tag.title = fixedString(data, 0, 30)
        tag.artist = fixedString(data, 30, 30)
        tag.album = fixedString(data, 60, 30)
        tag.year = fixedString(data, 90, 4)

        // ID3v1.1: a zero byte at comment[28] marks comment[29] as the track number
        if (data[122].toInt() == 0 && data[123].toInt() != 0) {
            tag.comment = fixedString(data, 94, 28)
            tag.track = u(data[123])
        } else {
            tag.comment = fixedString(data, 94, 30)
        }

        val genreId = u(data[124])
        tag.genre = GenreTypes.nameOf(genreId) ?: ""
        return tag
    }

    fun write(io: FileIo, tag: Id3v1Tag) {
        val data = ByteArray(TAG_LENGTH)
        data[0] = 'T'.code.toByte()
        data[1] = 'A'.code.toByte()
        data[2] = 'G'.code.toByte()
        putFixedString(data, 3, 30, tag.title)
        putFixedString(data, 33, 30, tag.artist)
        putFixedString(data, 63, 30, tag.album)
        putFixedString(data, 93, 4, tag.year)
        val track = tag.track
        if (track != null) {
            putFixedString(data, 97, 28, tag.comment)
            data[125] = 0
            data[126] = track.toByte()
        } else {
            putFixedString(data, 97, 30, tag.comment)
        }
        data[127] = (GenreTypes.idOf(tag.genre) ?: 0xFF).toByte()

        io.position = findTagStart(io) ?: io.size
        io.write(data)
        io.flush()
    }

    fun delete(io: FileIo) {
        val start = findTagStart(io) ?: return
        io.truncate(start)
        io.flush()
    }

    /** ISO-8859-1, terminated by the first NUL, trailing whitespace removed. */
    private fun fixedString(data: ByteArray, offset: Int, length: Int): String {
        val chars = CharArray(length)
        var end = 0
        while (end < length) {
            val b = u(data[offset + end])
            if (b == 0) break
            chars[end] = b.toChar()
            end++
        }
        return chars.concatToString(0, end).trimEnd()
    }

    private fun putFixedString(data: ByteArray, offset: Int, length: Int, value: String) {
        for (i in 0 until length) {
            val c = if (i < value.length) value[i].code else 0
            // ID3v1 is ISO-8859-1; anything outside becomes '?'
            data[offset + i] = if (c <= 0xFF) c.toByte() else '?'.code.toByte()
        }
    }
}
