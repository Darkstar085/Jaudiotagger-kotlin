package org.jaudiotagger.kt.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.IOException
import kotlinx.io.files.Path
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.SEEK_CUR
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.ftruncate
import platform.posix.lseek
import platform.posix.open
import platform.posix.read
import platform.posix.strerror
import platform.posix.write

@OptIn(ExperimentalForeignApi::class)
actual fun openFileIo(path: Path, readOnly: Boolean): FileIo {
    val flags = if (readOnly) O_RDONLY else O_RDWR
    val fd = open(path.toString(), flags)
    if (fd == -1) {
        throw IOException("Cannot open $path: ${strerror(errno)?.toKString() ?: errno}")
    }
    return PosixFileIo(fd)
}

@OptIn(ExperimentalForeignApi::class)
internal class PosixFileIo(private val fd: Int) : FileIo {

    override var position: Long = 0

    override val size: Long
        get() {
            val current = lseek(fd, 0, SEEK_CUR)
            val end = lseek(fd, 0, SEEK_END)
            lseek(fd, current, SEEK_SET)
            return end
        }

    override fun read(dest: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        lseek(fd, position, SEEK_SET)
        val n = dest.usePinned { pinned ->
            read(fd, pinned.addressOf(offset), length.toULong())
        }
        if (n < 0) throw IOException("read failed: errno $errno")
        if (n == 0L) return -1
        position += n
        return n.toInt()
    }

    override fun write(src: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            lseek(fd, position, SEEK_SET)
            val n = src.usePinned { pinned ->
                write(fd, pinned.addressOf(offset + written), (length - written).toULong())
            }
            if (n < 0) throw IOException("write failed: errno $errno")
            written += n.toInt()
            position += n
        }
    }

    override fun truncate(newSize: Long) {
        if (ftruncate(fd, newSize) != 0) throw IOException("ftruncate failed: errno $errno")
    }

    override fun flush() {
        fsync(fd)
    }

    override fun close() {
        close(fd)
    }
}
