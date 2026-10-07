package org.jaudiotagger.kt.io

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith

private const val FILE_SIZE = 300_000
private const val LARGE_READ = 3 * 1024 * 1024

private fun patternByte(i: Int): Byte = (i * 31 + i / 256).toByte()

private fun createPattern(size: Int): ByteArray =
    ByteArray(size) { i -> patternByte(i) }

@RunWith(AndroidJUnit4::class)
class FileDescriptorIoTest {

    private val cacheDir: File
        get() = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private val tempFiles = mutableListOf<File>()
    private val openPfds = mutableListOf<ParcelFileDescriptor>()

    @AfterTest
    fun tearDown() {
        openPfds.forEach { it.close() }
        openPfds.clear()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun openWithPattern(size: Int = FILE_SIZE): FileDescriptorIo {
        val file = File.createTempFile("fdio-test", ".bin", cacheDir)
        tempFiles += file
        file.writeBytes(createPattern(size))
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        openPfds += pfd
        return FileDescriptorIo(pfd.fileDescriptor)
    }

    @Test
    fun readWholeFile() {
        openWithPattern().use { io ->
            val dest = ByteArray(FILE_SIZE)
            val read = io.read(dest)
            assertEquals(FILE_SIZE, read)
            assertContentEquals(createPattern(FILE_SIZE), dest)
            assertEquals(FILE_SIZE.toLong(), io.position)
        }
    }

    @Test
    fun readPartialAtEnd() {
        openWithPattern().use { io ->
            io.position = FILE_SIZE - 1000L
            val dest = ByteArray(200_000)
            val read = io.read(dest)
            assertEquals(1000, read)
            val expected = createPattern(FILE_SIZE).copyOfRange(FILE_SIZE - 1000, FILE_SIZE)
            assertContentEquals(expected, dest.copyOf(1000))
            assertEquals(FILE_SIZE.toLong(), io.position)
        }
    }

    @Test
    fun readAtEofReturnsMinusOne() {
        openWithPattern().use { io ->
            io.position = FILE_SIZE.toLong()
            val dest = ByteArray(100)
            assertEquals(-1, io.read(dest))
        }
    }

    @Test
    fun readZeroLengthReturnsZero() {
        openWithPattern().use { io ->
            val dest = ByteArray(10)
            assertEquals(0, io.read(dest, 0, 0))
            assertEquals(0, io.position)
        }
    }

    @Test
    fun writeAtOffset() {
        val file = File.createTempFile("fdio-write-test", ".bin", cacheDir)
        tempFiles += file
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
            FileDescriptorIo(pfd.fileDescriptor).use { io ->
                val payload = createPattern(200_000)
                val src = ByteArray(200_500) { i ->
                    if (i < 500) 0 else payload[i - 500]
                }
                io.position = 1000
                io.write(src, 500, 200_000)
                io.flush()
                assertEquals(201_000, io.size)
                assertEquals(201_000, io.position)

                io.position = 1000
                val readBack = ByteArray(200_000)
                assertEquals(200_000, io.read(readBack))
                assertContentEquals(payload, readBack)
            }
        }
    }

    @Test
    fun truncateNeverExtends() {
        openWithPattern().use { io ->
            val originalSize = io.size
            io.truncate(originalSize + 100)
            assertEquals(originalSize, io.size)
        }
    }

    @Test
    fun truncateClampsPosition() {
        openWithPattern().use { io ->
            io.position = FILE_SIZE.toLong()
            io.truncate(1000)
            assertEquals(1000, io.position)
            assertEquals(1000, io.size)
        }
    }

    @Test
    fun readFromWriteOnlyDescriptorThrowsIoException() {
        val file = File.createTempFile("fdio-writeonly", ".bin", cacheDir)
        tempFiles += file
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY).use { pfd ->
            FileDescriptorIo(pfd.fileDescriptor).use { io ->
                assertFailsWith<java.io.IOException> {
                    io.read(ByteArray(4))
                }
            }
        }
    }

    @Test
    fun readLargerThanChunk() {
        openWithPattern(LARGE_READ).use { io ->
            val dest = ByteArray(LARGE_READ)
            val read = io.read(dest)
            assertEquals(LARGE_READ, read)
            assertContentEquals(createPattern(LARGE_READ), dest)
        }
    }
}
