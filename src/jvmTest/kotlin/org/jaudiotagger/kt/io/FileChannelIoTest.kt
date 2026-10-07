package org.jaudiotagger.kt.io

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val FILE_SIZE = 300_000

private fun patternByte(i: Int): Byte = (i * 31 + i / 256).toByte()

private fun createPattern(size: Int): ByteArray =
    ByteArray(size) { i -> patternByte(i) }

class FileChannelIoTest {

    private var tempFile: java.io.File? = null

    @AfterTest
    fun tearDown() {
        tempFile?.delete()
        tempFile = null
    }

    private fun openChannelWithPattern(): FileChannelIo {
        val file = java.io.File.createTempFile("filechannelio-test", ".bin")
        tempFile = file
        FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)
            .use { channel ->
                channel.write(ByteBuffer.wrap(createPattern(FILE_SIZE)))
            }
        return FileChannelIo(
            FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE),
        )
    }

    @Test
    fun readWholeFile() {
        openChannelWithPattern().use { io ->
            val dest = ByteArray(FILE_SIZE)
            val read = io.read(dest)
            assertEquals(FILE_SIZE, read)
            assertContentEquals(createPattern(FILE_SIZE), dest)
            assertEquals(FILE_SIZE.toLong(), io.position)
        }
    }

    @Test
    fun readPartialAtEnd() {
        openChannelWithPattern().use { io ->
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
        openChannelWithPattern().use { io ->
            io.position = FILE_SIZE.toLong()
            val dest = ByteArray(100)
            assertEquals(-1, io.read(dest))
        }
    }

    @Test
    fun readZeroLengthReturnsZero() {
        openChannelWithPattern().use { io ->
            val dest = ByteArray(10)
            assertEquals(0, io.read(dest, 0, 0))
            assertEquals(0, io.position)
        }
    }

    @Test
    fun writeAtOffset() {
        val file = java.io.File.createTempFile("filechannelio-write-test", ".bin")
        tempFile = file
        FileChannelIo(
            FileChannel.open(
                file.toPath(),
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
            ),
        ).use { io ->
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
