package org.jaudiotagger.kt.io

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.io.files.Path
import org.jaudiotagger.kt.AudioFormat
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.Tag
import org.jaudiotagger.kt.tag.id3.Id3v1Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileDescriptorIoParityTest {

    private data class Sample(val assetName: String, val useId3v2ForV1: Boolean = false)

    private val samples = listOf(
        Sample("issue52.mp3"),
        Sample("test23.mp3"),
        Sample("testV1Cbr128ID3v1.mp3", useId3v2ForV1 = true),
        Sample("testV1Cbr128.mp3", useId3v2ForV1 = true),
        Sample("test.flac"),
        Sample("test.ogg"),
        Sample("test.m4a"),
        Sample("test.wav"),
        Sample("test119.aif"),
        Sample("test1.wma"),
        Sample("test122.dsf"),
        Sample("test0001.ape"),
        Sample("test0001.wv"),
    )

    private val fieldNames = listOf(
        "TITLE",
        "ARTIST",
        "ALBUM",
        "ALBUM_ARTIST",
        "TRACK",
        "DISC_NO",
        "COMMENT",
        "LYRICS",
        "GENRE",
    )

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fileDescriptorIoMatchesFileChannelIo() {
        val pngBytes = pngAtLeast(200 * 1024)
        val failures = mutableListOf<String>()
        for (sample in samples) {
            try {
                runSampleParity(sample, pngBytes)
            } catch (e: Throwable) {
                failures += "${sample.assetName}: ${e.message ?: e::class.simpleName}"
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    private fun runSampleParity(sample: Sample, pngBytes: ByteArray) {
        val a = copyAssetToCache(sample.assetName, "a-${sample.assetName}")
        val b = copyAssetToCache(sample.assetName, "b-${sample.assetName}")
        val format = AudioFormat.forPath(Path(a.absolutePath))
        try {
            grow(a, b, format, sample, pngBytes)
            assertFilesEqual(a, b, sample.assetName, "grow")
            assertTitleThroughFd(b, format, "Tïtle 日本語")

            fit(a, b, format)
            assertFilesEqual(a, b, sample.assetName, "fit")
            assertTitleThroughFd(b, format, "t2")

            clear(a, b, format)
            assertFilesEqual(a, b, sample.assetName, "clear")

            delete(a, b, format)
            assertFilesEqual(a, b, sample.assetName, "delete")
        } finally {
            a.delete()
            b.delete()
        }
    }

    private fun grow(a: File, b: File, format: AudioFormat, sample: Sample, pngBytes: ByteArray) {
        withChannel(a) { io ->
            val tag = readTagForSample(io, format, sample)
            applyGrowFields(tag, pngBytes)
            AudioTagger.write(io, tag, format)
        }
        withFd(b) { io ->
            val tag = readTagForSample(io, format, sample)
            applyGrowFields(tag, pngBytes)
            AudioTagger.write(io, tag, format)
        }
    }

    private fun fit(a: File, b: File, format: AudioFormat) {
        withChannel(a) { io ->
            val tag = AudioTagger.read(io, format).tag
            tag.set(FieldKey.TITLE, "t2")
            AudioTagger.write(io, tag, format)
        }
        withFd(b) { io ->
            val tag = AudioTagger.read(io, format).tag
            tag.set(FieldKey.TITLE, "t2")
            AudioTagger.write(io, tag, format)
        }
    }

    private fun clear(a: File, b: File, format: AudioFormat) {
        withChannel(a) { io ->
            val tag = AudioTagger.read(io, format).tag
            for (field in fieldNames) {
                tag.remove(FieldKey.valueOf(field))
            }
            tag.clearArtworks()
            AudioTagger.write(io, tag, format)
        }
        withFd(b) { io ->
            val tag = AudioTagger.read(io, format).tag
            for (field in fieldNames) {
                tag.remove(FieldKey.valueOf(field))
            }
            tag.clearArtworks()
            AudioTagger.write(io, tag, format)
        }
    }

    private fun delete(a: File, b: File, format: AudioFormat) {
        withChannel(a) { io -> AudioTagger.deleteTag(io, format) }
        withFd(b) { io -> AudioTagger.deleteTag(io, format) }
    }

    private fun readTagForSample(io: FileIo, format: AudioFormat, sample: Sample): Tag {
        var tag = AudioTagger.read(io, format).tag
        if (sample.useId3v2ForV1 && tag is Id3v1Tag) {
            tag = Id3v2Tag(Id3v2Version.V23)
        }
        return tag
    }

    private fun applyGrowFields(tag: Tag, pngBytes: ByteArray) {
        tag.set(FieldKey.TITLE, "Tïtle 日本語")
        tag.set(FieldKey.ARTIST, "Artist")
        tag.set(FieldKey.ALBUM, "Album")
        tag.set(FieldKey.ALBUM_ARTIST, "Album Artist")
        tag.set(FieldKey.TRACK, "5")
        tag.set(FieldKey.DISC_NO, "2")
        tag.set(FieldKey.COMMENT, "Comment")
        tag.set(FieldKey.LYRICS, "Line 1\nLine 2")
        tag.set(FieldKey.GENRE, "Rock, Hardrock")
        tag.setArtwork(Artwork(data = pngBytes, mimeType = "image/png"))
    }

    private fun assertTitleThroughFd(file: File, format: AudioFormat, expected: String) {
        withFd(file) { io ->
            assertEquals(expected, AudioTagger.read(io, format).tag.first(FieldKey.TITLE))
        }
    }

    private inline fun <R> withChannel(file: File, block: (FileIo) -> R): R =
        openFileIo(Path(file.absolutePath), readOnly = false).use(block)

    private inline fun <R> withFd(file: File, block: (FileIo) -> R): R {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        return try {
            FileDescriptorIo(pfd.fileDescriptor).use(block)
        } finally {
            pfd.close()
        }
    }

    private fun copyAssetToCache(assetName: String, cacheName: String): File {
        val out = File(context.cacheDir, cacheName)
        context.assets.open("testdata/$assetName").use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun assertFilesEqual(a: File, b: File, sample: String, step: String) {
        check(a.length() == b.length()) {
            "$sample $step: size ${a.length()} vs ${b.length()}"
        }
        a.inputStream().use { inputA ->
            b.inputStream().use { inputB ->
                var offset = 0L
                val bufA = ByteArray(8192)
                val bufB = ByteArray(8192)
                while (true) {
                    val readA = inputA.read(bufA)
                    val readB = inputB.read(bufB)
                    if (readA <= 0 && readB <= 0) break
                    check(readA == readB) {
                        "$sample $step: read count differs at offset $offset"
                    }
                    for (i in 0 until readA) {
                        if (bufA[i] != bufB[i]) {
                            check(false) {
                                "$sample $step: first diff at $offset sha256(a)=${sha256(a)} sha256(b)=${
                                    sha256(
                                        b
                                    )
                                }"
                            }
                        }
                    }
                    offset += readA
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun pngAtLeast(minSize: Int): ByteArray {
        val original = context.assets.open("testdata/coverart.png").use { it.readBytes() }
        if (original.size >= minSize) return original
        val padding = minSize - original.size - 12
        val text = ByteArray(padding.coerceAtLeast(1)) { 'x'.code.toByte() }
        val chunk = pngChunk("tEXt", text)
        val withoutIend = original.copyOfRange(0, original.size - 12)
        val iend = original.copyOfRange(original.size - 12, original.size)
        return withoutIend + chunk + iend
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.encodeToByteArray()
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val crcValue = crc.value.toInt()
        val out = ByteArray(4 + 4 + data.size + 4)
        out[0] = (data.size ushr 24).toByte()
        out[1] = (data.size ushr 16).toByte()
        out[2] = (data.size ushr 8).toByte()
        out[3] = data.size.toByte()
        typeBytes.copyInto(out, 4)
        data.copyInto(out, 8)
        out[8 + data.size] = (crcValue ushr 24).toByte()
        out[9 + data.size] = (crcValue ushr 16).toByte()
        out[10 + data.size] = (crcValue ushr 8).toByte()
        out[11 + data.size] = crcValue.toByte()
        return out
    }
}
