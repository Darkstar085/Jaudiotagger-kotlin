package org.jaudiotagger.kt.io

/**
 * Shifts file content to grow or shrink the metadata area without loading
 * the whole file into memory. Ported from `org.jaudiotagger.utils.ShiftData`.
 */
internal object ShiftData {

    /** Chunk size for copy operations; kept moderate for low-memory devices. */
    const val CHUNK_SIZE = 1024 * 1024

    /**
     * Shifts everything from the current [FileIo.position] to end of file forward by
     * [offset] bytes (backward when negative, truncating the file).
     * Copies chunks starting from the end of the file so regions may overlap safely.
     */
    fun shiftDataByOffsetToMakeSpace(io: FileIo, offset: Int) {
        val origFileSize = io.size
        val startPos = io.position
        val amountToBeWritten = io.size - startPos
        val count = amountToBeWritten / CHUNK_SIZE
        val mod = amountToBeWritten % CHUNK_SIZE

        val chunk = ByteArray(CHUNK_SIZE)
        var readPos = io.size - CHUNK_SIZE
        var writePos = io.size - CHUNK_SIZE + offset

        for (i in 0 until count) {
            io.position = readPos
            val read = io.read(chunk, 0, CHUNK_SIZE)
            io.position = writePos
            io.write(chunk, 0, read)
            readPos -= CHUNK_SIZE
            writePos -= CHUNK_SIZE
        }

        if (mod > 0) {
            val rest = ByteArray(mod.toInt())
            io.position = startPos
            val read = io.read(rest)
            io.position = startPos + offset
            io.write(rest, 0, read)
        }

        if (offset < 0) {
            io.truncate(origFileSize + offset)
        }
    }

    /**
     * Shifts everything from the current [FileIo.position] to end of file backward by
     * [shrinkBy] bytes and truncates the file. Copies front-to-back, which is safe
     * because destination is always before source.
     */
    fun shiftDataByOffsetToShrinkSpace(io: FileIo, shrinkBy: Int) {
        val startPos = io.position
        val amountToBeWritten = io.size - startPos
        val count = amountToBeWritten / CHUNK_SIZE
        val mod = amountToBeWritten % CHUNK_SIZE

        val chunk = ByteArray(CHUNK_SIZE)
        var readPos = startPos
        var writePos = startPos - shrinkBy

        for (i in 0 until count) {
            io.position = readPos
            val read = io.read(chunk, 0, CHUNK_SIZE)
            io.position = writePos
            io.write(chunk, 0, read)
            readPos += CHUNK_SIZE
            writePos += CHUNK_SIZE
        }

        if (mod > 0) {
            val rest = ByteArray(mod.toInt())
            io.position = readPos
            val read = io.read(rest)
            io.position = writePos
            io.write(rest, 0, read)
        }

        io.truncate(startPos - shrinkBy + amountToBeWritten)
    }
}
