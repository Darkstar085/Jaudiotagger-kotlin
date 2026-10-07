package org.jaudiotagger.kt.audio.mp4

import org.jaudiotagger.kt.CannotReadException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.decodeLatin1
import org.jaudiotagger.kt.io.readInt32BE

/**
 * MP4/QuickTime atom (box) navigation over a [FileIo].
 *
 * An atom is: uint32 BE size (including the 8-byte header), 4-char type;
 * size == 1 means a 64-bit size follows; size == 0 means "to end of file".
 * The `meta` container carries 4 bytes of version/flags before its children.
 */
internal object Mp4Atoms {

    const val HEADER_LENGTH = 8

    class AtomInfo(val id: String, val dataStart: Long, val dataEnd: Long) {
        val dataLength: Long get() = dataEnd - dataStart
    }

    /** Extra preamble bytes inside container atoms before their children start. */
    private fun containerPreamble(id: String): Int = when (id) {
        "meta" -> 4 // version + flags
        "stsd" -> 8 // version + flags + entry count
        else -> 0
    }

    /** Iterates child atoms in [start, end). */
    inline fun forEachChild(io: FileIo, start: Long, end: Long, block: (AtomInfo) -> Unit) {
        var position = start
        while (position + HEADER_LENGTH <= end) {
            io.position = position
            val header = io.readFully(HEADER_LENGTH)
            var size = header.readInt32BE(0).toUInt().toLong()
            val id = header.decodeLatin1(4, 8)
            var dataStart = position + HEADER_LENGTH
            if (size == 1L) {
                val large = io.readFully(8)
                size = (large.readInt32BE(0).toUInt().toLong() shl 32) or
                    large.readInt32BE(4).toUInt().toLong()
                dataStart += 8
            } else if (size == 0L) {
                size = end - position
            }
            if (size < HEADER_LENGTH || position + size > end) break
            block(AtomInfo(id, dataStart, position + size))
            position += size
        }
    }

    /** Finds the first child with [id] directly inside [start, end). */
    fun findChild(io: FileIo, start: Long, end: Long, id: String): AtomInfo? {
        forEachChild(io, start, end) { atom ->
            if (atom.id == id) return atom
        }
        return null
    }

    /** Resolves a path of nested atoms from the file root, e.g. "moov", "udta", "meta", "ilst". */
    fun findPath(io: FileIo, vararg path: String): AtomInfo? {
        var start = 0L
        var end = io.size
        var found: AtomInfo? = null
        for (id in path) {
            found = findChild(io, start, end, id) ?: return null
            start = found.dataStart + containerPreamble(id)
            end = found.dataEnd
        }
        return found
    }

    fun requirePath(io: FileIo, vararg path: String): AtomInfo =
        findPath(io, *path) ?: throw CannotReadException("This file does not appear to be an audio file")
}
