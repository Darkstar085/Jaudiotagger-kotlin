package org.jaudiotagger.kt.io

import android.system.ErrnoException
import android.system.Os
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlinx.io.files.Path

// NIO copies a heap buffer through a temporary direct buffer of the same size and caches it
// per thread for the thread's lifetime; chunking caps that cache at this size.
private const val MAX_NIO_CHUNK = 64 * 1024

actual fun openFileIo(path: Path, readOnly: Boolean): FileIo {
    val raf = RandomAccessFile(path.toString(), if (readOnly) "r" else "rw")
    return FileChannelIo(raf.channel, raf)
}

/**
 * [FileIo] over a [FileChannel], for callers that already hold a channel.
 */
class FileChannelIo(
    private val channel: FileChannel,
    private val file: AutoCloseable? = null,
) : FileIo {

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
        if (file != null) {
            file.close()
        } else {
            channel.close()
        }
    }
}

fun FileChannel.asFileIo(): FileIo = FileChannelIo(this)

/**
 * [FileIo] over a raw [FileDescriptor] using POSIX calls — the MediaStore/SAF
 * path: obtain a descriptor with `contentResolver.openFileDescriptor(uri, "rw")`
 * and edit tags directly, no temp copies:
 *
 * ```kotlin
 * contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
 *     FileDescriptorIo(pfd.fileDescriptor).use { io ->
 *         val file = AudioTagger.read(io, AudioFormat.MP3)
 *         file.tag.set(FieldKey.TITLE, "New title")
 *         AudioTagger.write(io, file.tag, AudioFormat.MP3)
 *     }
 * }
 * ```
 *
 * Closing this object does not close the descriptor; the owner
 * (e.g. the ParcelFileDescriptor) stays responsible for it.
 */
class FileDescriptorIo(private val fd: FileDescriptor) : FileIo {

    override var position: Long = 0

    override val size: Long
        get() = errnoAsIo { Os.fstat(fd).st_size }

    override fun read(dest: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var total = 0
        while (total < length) {
            val read = errnoAsIo { Os.pread(fd, dest, offset + total, length - total, position) }
            if (read == 0) break
            total += read
            position += read
        }
        return if (total == 0) -1 else total
    }

    override fun write(src: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            val n = errnoAsIo { Os.pwrite(fd, src, offset + written, length - written, position) }
            if (n <= 0) throw kotlinx.io.IOException("pwrite returned $n")
            written += n
            position += n
        }
    }

    // FileChannel.truncate semantics: never extends the file, clamps the position
    override fun truncate(newSize: Long) {
        if (newSize < size) {
            errnoAsIo { Os.ftruncate(fd, newSize) }
        }
        if (position > newSize) {
            position = newSize
        }
    }

    override fun flush() {
        errnoAsIo { Os.fsync(fd) }
    }

    override fun close() {
        // deliberately not closing: the descriptor belongs to the caller
    }
}

private inline fun <T> errnoAsIo(block: () -> T): T {
    try {
        return block()
    } catch (e: ErrnoException) {
        throw e.rethrowAsIOException()
    }
}
