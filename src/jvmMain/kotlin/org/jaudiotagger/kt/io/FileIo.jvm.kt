package org.jaudiotagger.kt.io

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.io.files.Path

// NIO copies a heap buffer through a temporary direct buffer of the same size and caches it
// per thread for the thread's lifetime; chunking caps that cache at this size.
private const val MAX_NIO_CHUNK = 64 * 1024

actual fun openFileIo(path: Path, readOnly: Boolean): FileIo {
    val options = if (readOnly) {
        arrayOf(StandardOpenOption.READ)
    } else {
        arrayOf(StandardOpenOption.READ, StandardOpenOption.WRITE)
    }
    return FileChannelIo(FileChannel.open(java.nio.file.Path.of(path.toString()), *options))
}

/**
 * [FileIo] over any [FileChannel]. Public so callers holding a channel from
 * elsewhere (e.g. one created from an Android file descriptor) can pass it in
 * without going through a filesystem path.
 */
class FileChannelIo(private val channel: FileChannel) : FileIo {

    override var position: Long
        get() = channel.position()
        set(value) {
            channel.position(value)
        }

    override val size: Long
        get() = channel.size()

    override fun read(dest: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val chunk = minOf(MAX_NIO_CHUNK, length - total)
            val read = channel.read(ByteBuffer.wrap(dest, offset + total, chunk))
            if (read < 0) {
                return if (total == 0) -1 else total
            }
            total += read
            if (read < chunk) break
        }
        return total
    }

    override fun write(src: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            val chunk = minOf(MAX_NIO_CHUNK, length - written)
            val buffer = ByteBuffer.wrap(src, offset + written, chunk)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            written += chunk
        }
    }

    override fun truncate(newSize: Long) {
        channel.truncate(newSize)
    }

    override fun flush() {
        channel.force(false)
    }

    override fun close() {
        channel.close()
    }
}

fun FileChannel.asFileIo(): FileIo = FileChannelIo(this)
