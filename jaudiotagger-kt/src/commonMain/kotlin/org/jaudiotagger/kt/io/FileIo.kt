package org.jaudiotagger.kt.io

import kotlinx.io.files.Path

/**
 * Random-access file abstraction.
 *
 * Audio tagging needs positioned reads/writes and truncation, which streaming APIs
 * (kotlinx-io Source/Sink) do not provide, so each platform supplies its own
 * implementation (FileChannel on JVM, POSIX descriptors on native).
 *
 * The interface mirrors `java.nio.channels.FileChannel` semantics: an explicit
 * [position] that reads and writes advance.
 */
interface FileIo : AutoCloseable {

    /** Current read/write position in bytes from the start of the file. */
    var position: Long

    /** Current file size in bytes. */
    val size: Long

    /**
     * Reads up to [length] bytes at [position] into [dest] starting at [offset].
     * Advances [position] by the number of bytes read.
     *
     * @return number of bytes read, or -1 at end of file
     */
    fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size - offset): Int

    /**
     * Writes [length] bytes from [src] starting at [offset] at the current [position].
     * Advances [position] by [length].
     */
    fun write(src: ByteArray, offset: Int = 0, length: Int = src.size - offset)

    /** Truncates the file to [newSize] bytes. */
    fun truncate(newSize: Long)

    /** Forces buffered writes to storage. */
    fun flush()
}

/** Opens [path] for random access. The file must exist. */
expect fun openFileIo(path: Path, readOnly: Boolean = true): FileIo

/**
 * Reads exactly [length] bytes or throws [kotlinx.io.EOFException].
 */
fun FileIo.readFully(length: Int): ByteArray {
    val result = ByteArray(length)
    var read = 0
    while (read < length) {
        val n = read(result, read, length - read)
        if (n <= 0) throw kotlinx.io.EOFException(
            "Unable to read required number of bytes, read:$read:required:$length"
        )
        read += n
    }
    return result
}

/** Runs [block] with an open [FileIo] and always closes it. */
inline fun <R> withFileIo(path: Path, readOnly: Boolean = true, block: (FileIo) -> R): R =
    openFileIo(path, readOnly).use(block)
