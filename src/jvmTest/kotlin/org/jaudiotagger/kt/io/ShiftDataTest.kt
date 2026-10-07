package org.jaudiotagger.kt.io

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val FILE_SIZE = 50_000

private fun patternByte(i: Int): Byte = (i * 31 + i / 256).toByte()

class ShiftDataTest {

    private var tempFile: java.io.File? = null

    @AfterTest
    fun tearDown() {
        tempFile?.delete()
        tempFile = null
    }

    @Test
    fun shrinkAtEndTruncatesFile() {
        val file = java.io.File.createTempFile("shiftdata-shrink", ".bin")
        tempFile = file
        FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)
            .use { channel ->
                channel.write(ByteBuffer.wrap(ByteArray(FILE_SIZE) { i -> patternByte(i) }))
            }
        val shrinkBy = 1234
        FileChannelIo(
            FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE),
        ).use { io ->
            io.position = FILE_SIZE.toLong()
            ShiftData.shiftDataByOffsetToShrinkSpace(io, shrinkBy)
            assertEquals((FILE_SIZE - shrinkBy).toLong(), io.size)
        }
    }
}
