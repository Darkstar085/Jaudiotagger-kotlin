package org.jaudiotagger.kt.parity

import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.kt.AudioFormat
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.copyToTemp
import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey as KtFieldKey
import org.jaudiotagger.kt.tag.id3.Id3v1Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2Version
import org.jaudiotagger.kt.testDataDir
import org.jaudiotagger.kt.testDataPath
import org.jaudiotagger.tag.FieldKey as JavaFieldKey
import org.jaudiotagger.tag.KeyNotFoundException
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagException
import org.jaudiotagger.kt.tag.asf.AsfTag
import java.util.zip.CRC32

class JavaParityTest {

    private val fieldNames = listOf(
        "TITLE", "ARTIST", "ALBUM", "ALBUM_ARTIST", "TRACK", "DISC_NO", "COMMENT", "LYRICS", "GENRE",
    )

    private data class Mismatch(val file: String, val field: String, val java: String?, val kt: String?)

    private data class AcceptedDeviation(val file: String, val field: String, val reason: String)

    private data class WriteCase(val name: String, val sample: String, val useId3v2ForV1: Boolean = false)

    /** Known differences accepted by design or upstream format quirks. */
    private val acceptedDeviations = listOf(
        AcceptedDeviation("bug153.wav", "TITLE", "LIST-INFO vs Java READ_ID3_ONLY"),
        AcceptedDeviation("bug153.wav", "ALBUM", "LIST-INFO vs Java READ_ID3_ONLY"),
        AcceptedDeviation("bug153.wav", "TRACK", "LIST-INFO vs Java READ_ID3_ONLY"),
        AcceptedDeviation("test127.wav", "COMMENT", "Java keeps RIFF pad byte in INFO"),
        AcceptedDeviation("test128.wav", "COMMENT", "Java keeps RIFF pad byte in INFO"),
        AcceptedDeviation("test152.aiff", "ARTIST", "Java tolerates misaligned ID3 chunk"),
        AcceptedDeviation(
            "testV2L3Stereo.mp3",
            "duration",
            "Java MPEG-2 L3 stereo uses 1152 samples/frame; kt uses 576 per ISO/IEC 13818-3",
        ),
    )

    private fun acceptedReason(file: String, field: String): String? {
        if (file == "test123.wav") return "Java keeps RIFF pad byte in INFO"
        return acceptedDeviations.find { it.file == file && it.field == field }?.reason
    }

    @Test
    fun readParityOverCorpus() {
        val mismatches = mutableListOf<Mismatch>()
        val accepted = mutableListOf<Mismatch>()
        val javaUnreadable = mutableListOf<String>()
        var id3v1OnlyCount = 0

        for (file in corpusFiles()) {
            val name = file.name
            val ktPath = Path(file.absolutePath)

            val javaAudio = try {
                AudioFileIO.read(file)
            } catch (_: Exception) {
                javaUnreadable += name
                continue
            }

            val ktAudio = try {
                AudioTagger.read(ktPath)
            } catch (e: Exception) {
                mismatches += Mismatch(name, "<read>", "ok", e.message)
                continue
            }

            if (ktAudio.tag is Id3v1Tag) {
                id3v1OnlyCount++
                continue
            }

            val javaTag = try {
                javaAudio.tagOrCreateDefault
            } catch (_: RuntimeException) {
                javaAudio.tag ?: continue
            }
            val ktTag = ktAudio.tag

            for (fieldName in fieldNames) {
                val javaValue = javaField(javaTag, JavaFieldKey.valueOf(fieldName))
                val ktValue = ktField(ktTag, KtFieldKey.valueOf(fieldName))
                if (javaValue != ktValue) {
                    val row = Mismatch(name, fieldName, javaValue, ktValue)
                    if (acceptedReason(name, fieldName) != null) {
                        accepted += row
                    } else {
                        mismatches += row
                    }
                }
            }

            val javaArt = javaTag.firstArtwork?.binaryData
            val ktArt = ktTag.artworks.firstOrNull()?.data
            if (javaArt != null && ktArt != null) {
                if (!javaArt.contentEquals(ktArt)) {
                    mismatches += Mismatch(name, "artwork", "<${javaArt.size} bytes>", "<${ktArt.size} bytes>")
                }
            } else if (javaArt != null || ktArt != null) {
                mismatches += Mismatch(
                    name,
                    "artwork",
                    javaArt?.let { "<${it.size} bytes>" },
                    ktArt?.let { "<${it.size} bytes>" },
                )
            }

            val javaDuration = javaAudio.audioHeader.trackLength.toLong()
            val ktDuration = ktAudio.properties.duration.inWholeSeconds
            if (kotlin.math.abs(javaDuration - ktDuration) > 1) {
                mismatches += Mismatch(name, "duration", javaDuration.toString(), ktDuration.toString())
            }
        }

        reportReadParity(mismatches, accepted, javaUnreadable, id3v1OnlyCount)
        assertTrue(mismatches.isEmpty(), "Parity mismatches remain; see test output")
    }

    @Test
    fun writeRoundTripPerFormat() {
        val pngBytes = File(testDataDir(), "coverart.png").readBytes()
        val failures = mutableListOf<String>()

        val cases = listOf(
            WriteCase("MP3 ID3v2.3", "issue52.mp3"),
            WriteCase("MP3 ID3v2.4", "test23.mp3"),
            WriteCase("MP3 ID3v1 only", "testV1Cbr128ID3v1.mp3", useId3v2ForV1 = true),
            WriteCase("MP3 tagless", "testV1Cbr128.mp3", useId3v2ForV1 = true),
            WriteCase("FLAC", "test.flac"),
            WriteCase("Ogg Vorbis", "test.ogg"),
            WriteCase("M4A", "test.m4a"),
            WriteCase("WAV", "test.wav"),
            WriteCase("AIFF", "test119.aif"),
            WriteCase("WMA", "test1.wma"),
            WriteCase("DSF", "test122.dsf"),
        )

        for (case in cases) {
            if (SystemFileSystem.metadataOrNull(testDataPath(case.sample)) == null) {
                failures += "${case.name}: sample ${case.sample} missing"
                continue
            }
            try {
                runWriteRoundTrip(case, pngBytes)
            } catch (e: Throwable) {
                failures += "${case.name}: ${e.message}"
            }
        }

        println("Write round trip results:")
        for (case in cases) {
            val caseFailures = failures.filter { it.startsWith(case.name) }
            println("  ${case.name}: ${if (caseFailures.isEmpty()) "PASS" else "FAIL: ${caseFailures.joinToString("; ")}"}")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun noOpWritePreservesJavaView() {
        val mismatches = mutableListOf<Mismatch>()
        val skipped = mutableListOf<String>()
        for (file in corpusFiles()) {
            val name = file.name
            if (!isWritableFormat(name)) continue
            val ktPath = Path(file.absolutePath)
            try {
                AudioFileIO.read(file)
            } catch (_: Exception) {
                skipped += name
                continue
            }
            try {
                AudioTagger.read(ktPath)
            } catch (_: Exception) {
                skipped += name
                continue
            }
            if (AudioTagger.read(ktPath).tag is Id3v1Tag) continue

            val path = copyToTemp(name, "noop-$name")
            try {
                val before = javaSnapshot(File(path.toString()))
                val wmaGapBefore = if (name.endsWith(".wma", ignoreCase = true)) {
                    wmaFileSizeGap(File(path.toString()))
                } else {
                    null
                }
                val tag = AudioTagger.read(path).tag
                AudioTagger.write(path, tag)
                if (wmaGapBefore != null) {
                    assertWmaFileSizeGapPreserved(wmaGapBefore, File(path.toString()), "no-op $name")
                }
                val after = javaSnapshot(File(path.toString()))
                compareSnapshots(name, before, after, mismatches)
            } catch (e: Throwable) {
                mismatches += Mismatch(name, "<write>", "ok", e.message ?: e.javaClass.simpleName)
            } finally {
                SystemFileSystem.delete(path)
            }
        }
        println("No-op write skipped (${skipped.size}): ${skipped.sorted().joinToString(", ")}")
        reportSnapshotMismatches("No-op write mismatches", mismatches)
        assertTrue(mismatches.isEmpty(), "No-op write mismatches remain; see test output")
    }

    @Test
    fun wmaLargeCoverRoundTrip() {
        val path = copyToTemp("test1.wma", "wma-large-cover")
        try {
            val file = File(path.toString())
            val gapBefore = wmaFileSizeGap(file)
            val png = pngAtLeast(70 * 1024)
            val tag = AudioTagger.read(path).tag as AsfTag
            tag.setArtwork(Artwork(data = png, mimeType = "image/png", description = "large"))
            AudioTagger.write(path, tag)
            assertWmaFileSizeGapPreserved(gapBefore, file, "large cover")
            val javaArt = javaTagFor(File(path.toString())).firstArtwork?.binaryData
            val ktArt = AudioTagger.read(path).tag.artworks.firstOrNull()?.data
            check(javaArt != null && javaArt.contentEquals(png)) { "Java large cover mismatch" }
            check(ktArt != null && ktArt.contentEquals(png)) { "kt large cover mismatch" }
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun wmaMultiValueGenre() {
        val path = copyToTemp("test1.wma", "wma-multi-genre")
        try {
            val file = File(path.toString())
            val gapBefore = wmaFileSizeGap(file)
            val tag = AudioTagger.read(path).tag as AsfTag
            tag.set(KtFieldKey.GENRE, "A")
            tag.add(KtFieldKey.GENRE, "B")
            AudioTagger.write(path, tag)
            assertWmaFileSizeGapPreserved(gapBefore, file, "multi-value genre")
            val javaGenres = javaAll(javaTagFor(File(path.toString())), JavaFieldKey.GENRE)
            val ktGenres = AudioTagger.read(path).tag.all(KtFieldKey.GENRE)
            check(javaGenres == listOf("A", "B")) { "Java genres=$javaGenres" }
            check(ktGenres == listOf("A", "B")) { "kt genres=$ktGenres" }
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun wmaReplaceArtwork() {
        val path = copyToTemp("test1.wma", "wma-replace-art")
        try {
            val file = File(path.toString())
            val gapBefore = wmaFileSizeGap(file)
            val small = File(testDataDir(), "coverart.png").readBytes()
            val tag = AudioTagger.read(path).tag as AsfTag
            check(tag.artworks.isNotEmpty()) { "test1.wma has no cover to replace" }
            tag.setArtwork(Artwork(data = small, mimeType = "image/png", description = "replaced"))
            AudioTagger.write(path, tag)
            assertWmaFileSizeGapPreserved(gapBefore, file, "replace artwork")
            val javaArts = javaTagFor(File(path.toString())).artworkList.map { it.binaryData }
            val ktArts = AudioTagger.read(path).tag.artworks.map { it.data }
            check(javaArts.size == 1 && javaArts[0].contentEquals(small)) { "Java replace artwork failed" }
            check(ktArts.size == 1 && ktArts[0].contentEquals(small)) { "kt replace artwork failed" }
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    @Test
    fun numberTotalPreservation() {
        val failures = mutableListOf<String>()
        for (sample in listOf("issue52.mp3", "test23.mp3", "test.m4a")) {
            try {
                runNumberTotalCase(sample)
            } catch (e: Throwable) {
                failures += "$sample: ${e.message}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun runWriteRoundTrip(case: WriteCase, pngBytes: ByteArray) {
        val path = if (case.name == "MP3 tagless") {
            val p = copyToTemp(case.sample, "parity-tagless")
            AudioTagger.deleteTag(p)
            p
        } else {
            copyToTemp(case.sample, "parity-${case.name.replace(' ', '-')}")
        }

        val original = AudioTagger.read(path)
        val originalJavaDuration = AudioFileIO.read(File(path.toString())).audioHeader.trackLength
        val audioStart = original.properties.audioDataStartPosition
        val audioEnd = original.properties.audioDataEndPosition
        val audioBefore = if (audioStart >= 0 && audioEnd > audioStart) {
            readRange(path, audioStart, audioEnd)
        } else {
            null
        }

        var tag: org.jaudiotagger.kt.tag.Tag = original.tag
        if (case.useId3v2ForV1 && tag is Id3v1Tag) {
            tag = Id3v2Tag(Id3v2Version.V23)
        }

        tag.set(KtFieldKey.TITLE, "Tïtle 日本語")
        tag.set(KtFieldKey.ARTIST, "Artist")
        tag.set(KtFieldKey.ALBUM, "Album")
        tag.set(KtFieldKey.ALBUM_ARTIST, "Album Artist")
        tag.set(KtFieldKey.TRACK, "5")
        tag.set(KtFieldKey.DISC_NO, "2")
        tag.set(KtFieldKey.COMMENT, "Comment")
        tag.set(KtFieldKey.LYRICS, "Line 1\nLine 2")
        tag.set(KtFieldKey.GENRE, "Rock, Hardrock")
        tag.setArtwork(Artwork(data = pngBytes, mimeType = "image/png"))
        val wmaGapBefore = if (case.sample.endsWith(".wma", ignoreCase = true)) {
            wmaFileSizeGap(File(path.toString()))
        } else {
            null
        }
        AudioTagger.write(path, tag)
        if (wmaGapBefore != null) {
            assertWmaFileSizeGapPreserved(wmaGapBefore, File(path.toString()), case.name)
        }

        verifyWrittenFields(path, pngBytes, case.name)

        if (case.name == "M4A") {
            val rockPath = copyToTemp(case.sample, "parity-m4a-rock")
            val rockTag = AudioTagger.read(rockPath).tag
            rockTag.set(KtFieldKey.GENRE, "Rock")
            AudioTagger.write(rockPath, rockTag)
            check(verifyGenreRock(rockPath)) { "M4A Rock genre round trip failed" }
            SystemFileSystem.delete(rockPath)
        }

        val afterWrite = AudioFileIO.read(File(path.toString()))
        check(kotlin.math.abs(afterWrite.audioHeader.trackLength - originalJavaDuration) < 0.01) {
            "${case.name}: audio duration changed"
        }
        val afterKt = AudioTagger.read(path)
        if (audioBefore != null &&
            audioStart == afterKt.properties.audioDataStartPosition &&
            audioEnd == afterKt.properties.audioDataEndPosition
        ) {
            val audioAfter = readRange(path, audioStart, audioEnd)
            check(audioBefore.contentEquals(audioAfter)) { "${case.name}: audio bytes changed" }
        }

        val cleared = AudioTagger.read(path).tag
        for (fieldName in fieldNames) cleared.remove(KtFieldKey.valueOf(fieldName))
        cleared.clearArtworks()
        AudioTagger.write(path, cleared)

        val javaCleared = javaTagFor(File(path.toString()))
        for (fieldName in fieldNames) {
            val javaValue = javaField(javaCleared, JavaFieldKey.valueOf(fieldName))
            check(javaValue == null) { "${case.name}: Java $fieldName=$javaValue after clear" }
        }
        check(javaCleared.firstArtwork == null) { "${case.name}: Java artwork remains after clear" }

        val ktCleared = AudioTagger.read(path).tag
        for (fieldName in fieldNames) {
            check(ktCleared.first(KtFieldKey.valueOf(fieldName)) == null) {
                "${case.name}: kt $fieldName after clear"
            }
        }
        check(ktCleared.artworks.isEmpty()) { "${case.name}: kt artwork after clear" }

        SystemFileSystem.delete(path)
    }

    private fun verifyWrittenFields(path: Path, pngBytes: ByteArray, caseName: String) {
        val expected = linkedMapOf(
            "TITLE" to "Tïtle 日本語",
            "ARTIST" to "Artist",
            "ALBUM" to "Album",
            "ALBUM_ARTIST" to "Album Artist",
            "TRACK" to "5",
            "DISC_NO" to "2",
            "COMMENT" to "Comment",
            "LYRICS" to "Line 1\nLine 2",
            "GENRE" to "Rock, Hardrock",
        )

        val javaTag = javaTagFor(File(path.toString()))
        val ktTag = AudioTagger.read(path).tag

        for ((fieldName, value) in expected) {
            val javaValue = javaField(javaTag, JavaFieldKey.valueOf(fieldName))
            check(javaValue == value) {
                "$caseName Java $fieldName=$javaValue expected $value"
            }
            check(ktTag.first(KtFieldKey.valueOf(fieldName)) == value) {
                "$caseName kt $fieldName=${ktTag.first(KtFieldKey.valueOf(fieldName))} expected $value"
            }
        }

        val javaArt = javaTag.firstArtwork?.binaryData
        val ktArt = ktTag.artworks.firstOrNull()?.data
        check(javaArt != null && javaArt.contentEquals(pngBytes)) { "$caseName Java artwork mismatch" }
        check(ktArt != null && ktArt.contentEquals(pngBytes)) { "$caseName kt artwork mismatch" }
    }

    private fun verifyGenreRock(path: Path): Boolean {
        val javaTag = javaTagFor(File(path.toString()))
        val ktTag = AudioTagger.read(path).tag
        return javaField(javaTag, JavaFieldKey.GENRE) == "Rock" &&
            ktTag.first(KtFieldKey.GENRE) == "Rock"
    }

    private fun runNumberTotalCase(sample: String) {
        val path = copyToTemp(sample, "parity-num-$sample")
        val tag = AudioTagger.read(path).tag
        tag.set(KtFieldKey.TRACK, "3")
        tag.set(KtFieldKey.TRACK_TOTAL, "12")
        tag.set(KtFieldKey.DISC_NO, "1")
        tag.set(KtFieldKey.DISC_TOTAL, "2")
        AudioTagger.write(path, tag)

        verifyNumberTotals(path, track = "3", trackTotal = "12", disc = "1", discTotal = "2")

        val mid = AudioTagger.read(path).tag
        mid.set(KtFieldKey.TRACK, "5")
        AudioTagger.write(path, mid)
        verifyNumberTotals(path, track = "5", trackTotal = "12", disc = "1", discTotal = "2")

        val afterRemove = AudioTagger.read(path).tag
        afterRemove.remove(KtFieldKey.TRACK)
        AudioTagger.write(path, afterRemove)
        verifyNumberTotals(path, track = null, trackTotal = "12", disc = "1", discTotal = "2")

        SystemFileSystem.delete(path)
    }

    private fun verifyNumberTotals(
        path: Path,
        track: String?,
        trackTotal: String,
        disc: String,
        discTotal: String,
    ) {
        val javaTag = javaTagFor(File(path.toString()))
        val ktTag = AudioTagger.read(path).tag
        check(normalizeJavaNumber(javaField(javaTag, JavaFieldKey.TRACK)) == track) {
            "Java TRACK=${javaField(javaTag, JavaFieldKey.TRACK)} expected $track"
        }
        check(ktTag.first(KtFieldKey.TRACK) == track) {
            "kt TRACK=${ktTag.first(KtFieldKey.TRACK)} expected $track"
        }
        check(javaField(javaTag, JavaFieldKey.TRACK_TOTAL) == trackTotal) {
            "Java TRACK_TOTAL=${javaField(javaTag, JavaFieldKey.TRACK_TOTAL)} expected $trackTotal"
        }
        check(ktTag.first(KtFieldKey.TRACK_TOTAL) == trackTotal) {
            "kt TRACK_TOTAL=${ktTag.first(KtFieldKey.TRACK_TOTAL)} expected $trackTotal"
        }
        check(javaField(javaTag, JavaFieldKey.DISC_NO) == disc) {
            "Java DISC_NO=${javaField(javaTag, JavaFieldKey.DISC_NO)} expected $disc"
        }
        check(ktTag.first(KtFieldKey.DISC_NO) == disc) {
            "kt DISC_NO=${ktTag.first(KtFieldKey.DISC_NO)} expected $disc"
        }
        check(javaField(javaTag, JavaFieldKey.DISC_TOTAL) == discTotal) {
            "Java DISC_TOTAL=${javaField(javaTag, JavaFieldKey.DISC_TOTAL)} expected $discTotal"
        }
        check(ktTag.first(KtFieldKey.DISC_TOTAL) == discTotal) {
            "kt DISC_TOTAL=${ktTag.first(KtFieldKey.DISC_TOTAL)} expected $discTotal"
        }
    }

    private fun reportReadParity(
        mismatches: List<Mismatch>,
        accepted: List<Mismatch>,
        javaUnreadable: List<String>,
        id3v1OnlyCount: Int,
    ) {
        println("Java-unreadable (${javaUnreadable.size}): ${javaUnreadable.sorted().joinToString(", ")}")
        println("ID3v1-only (expected): $id3v1OnlyCount")
        if (accepted.isNotEmpty()) {
            println("Accepted deviations (${accepted.size}):")
            accepted.sortedWith(compareBy({ it.file }, { it.field })).forEach {
                val reason = acceptedReason(it.file, it.field)
                println("  ${it.file} | ${it.field} | java=${it.java} kt=${it.kt} ($reason)")
            }
        }
        if (mismatches.isEmpty()) {
            println("No read parity mismatches.")
            return
        }
        println("Read parity mismatches (${mismatches.size}):")
        mismatches.groupBy { "${it.field}|${it.java}|${it.kt}" }.forEach { (key, rows) ->
            val parts = key.split("|", limit = 3)
            val files = rows.map { it.file }.sorted()
            println(
                "  ${parts[0]}: java=${parts.getOrNull(1)} kt=${parts.getOrNull(2)} -> " +
                    "${files.size} files: ${files.take(5).joinToString()}${if (files.size > 5) "..." else ""}",
            )
        }
        println("Full table:")
        mismatches.sortedWith(compareBy({ it.file }, { it.field })).forEach {
            println("  ${it.file} | ${it.field} | ${it.java} | ${it.kt}")
        }
    }

    private fun corpusFiles(): List<File> {
        val dir = File(testDataDir())
        return dir.listFiles()?.filter { file ->
            file.isFile && AudioFormat.fromExtension(file.extension) != null
        }?.sortedBy { it.name } ?: emptyList()
    }

    private fun javaTagFor(file: File): Tag {
        val audio = AudioFileIO.read(file)
        return try {
            audio.tagOrCreateDefault
        } catch (_: RuntimeException) {
            audio.tag ?: throw IllegalStateException("No Java tag for ${file.name}")
        }
    }

    private fun javaField(tag: Tag, field: JavaFieldKey): String? = try {
        tag.getFirst(field).normalizeEmpty()
    } catch (_: KeyNotFoundException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    } catch (_: TagException) {
        null
    }

    private fun ktField(tag: org.jaudiotagger.kt.tag.Tag, field: KtFieldKey): String? = try {
        tag.first(field).normalizeEmpty()
    } catch (_: Exception) {
        "THREW"
    }

    private fun String?.normalizeEmpty(): String? = if (isNullOrEmpty()) null else this

    /** Java number/total frames return "" or "0" when the part was zeroed out. */
    private fun normalizeJavaNumber(value: String?): String? =
        if (value.isNullOrEmpty() || value == "0") null else value

    private data class TagSnapshot(
        val fields: Map<String, List<String>>,
        val artworks: List<ByteArray>,
    )

    private fun isWritableFormat(fileName: String): Boolean {
        val format = AudioFormat.fromExtension(fileName.substringAfterLast('.', "")) ?: return false
        return format !in setOf(AudioFormat.REALAUDIO, AudioFormat.DFF)
    }

    private fun javaSnapshot(file: File): TagSnapshot {
        val tag = javaTagFor(file)
        val fields = fieldNames.associateWith { fieldName ->
            javaAll(tag, JavaFieldKey.valueOf(fieldName))
        }
        val artworks = tag.artworkList.mapNotNull { it.binaryData?.copyOf() }
        return TagSnapshot(fields, artworks)
    }

    private fun javaAll(tag: Tag, field: JavaFieldKey): List<String> = try {
        tag.getAll(field).mapNotNull { it.normalizeEmpty() }
    } catch (_: KeyNotFoundException) {
        emptyList()
    } catch (_: UnsupportedOperationException) {
        emptyList()
    } catch (_: TagException) {
        emptyList()
    }

    private fun compareSnapshots(
        file: String,
        before: TagSnapshot,
        after: TagSnapshot,
        mismatches: MutableList<Mismatch>,
    ) {
        for (fieldName in fieldNames) {
            val b = before.fields[fieldName].orEmpty()
            val a = after.fields[fieldName].orEmpty()
            if (b != a) {
                mismatches += Mismatch(file, fieldName, b.joinToString("|"), a.joinToString("|"))
            }
        }
        if (before.artworks.size != after.artworks.size) {
            mismatches += Mismatch(
                file,
                "artwork-count",
                before.artworks.size.toString(),
                after.artworks.size.toString(),
            )
        } else {
            before.artworks.zip(after.artworks).forEachIndexed { index, (b, a) ->
                if (!b.contentEquals(a)) {
                    mismatches += Mismatch(file, "artwork[$index]", "<${b.size} bytes>", "<${a.size} bytes>")
                }
            }
        }
    }

    private fun reportSnapshotMismatches(title: String, mismatches: List<Mismatch>) {
        if (mismatches.isEmpty()) {
            println("No $title.")
            return
        }
        println("$title (${mismatches.size}):")
        mismatches.groupBy { "${it.field}|${it.java}|${it.kt}" }.forEach { (key, rows) ->
            val parts = key.split("|", limit = 3)
            val files = rows.map { it.file }.sorted()
            println(
                "  ${parts[0]}: before=${parts.getOrNull(1)} after=${parts.getOrNull(2)} -> " +
                    "${files.size} files: ${files.take(5).joinToString()}${if (files.size > 5) "..." else ""}",
            )
        }
    }

    private fun pngAtLeast(minSize: Int): ByteArray {
        val original = File(testDataDir(), "coverart.png").readBytes()
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
        out[out.size - 4] = (crcValue ushr 24).toByte()
        out[out.size - 3] = (crcValue ushr 16).toByte()
        out[out.size - 2] = (crcValue ushr 8).toByte()
        out[out.size - 1] = crcValue.toByte()
        return out
    }

    private fun readRange(path: Path, start: Long, end: Long): ByteArray {
        val bytes = ByteArray((end - start).toInt())
        RandomAccessFile(path.toString(), "r").use { raf ->
            raf.seek(start)
            raf.readFully(bytes)
        }
        return bytes
    }

    /** Stored File Properties file size minus actual file length (Java AsfFileReader uses the same check). */
    private fun wmaFileSizeGap(file: File): Long = readWmaStoredFileSize(file) - file.length()

    private fun assertWmaFileSizeGapPreserved(gapBefore: Long, file: File, context: String) {
        val gapAfter = wmaFileSizeGap(file)
        check(gapAfter == gapBefore) {
            "$context: WMA stored-file-size gap changed from $gapBefore to $gapAfter " +
                "(stored=${readWmaStoredFileSize(file)}, actual=${file.length()})"
        }
    }

    private fun readWmaStoredFileSize(file: File): Long {
        RandomAccessFile(file, "r").use { raf ->
            val headerGuid = ByteArray(16)
            raf.readFully(headerGuid)
            check(headerGuid.contentEquals(ASF_HEADER_GUID)) { "${file.name}: not an ASF file" }
            val headerSize = readUInt64LE(raf)
            var position = 30L
            while (position + 24 <= headerSize) {
                raf.seek(position)
                val guid = ByteArray(16)
                raf.readFully(guid)
                val chunkSize = readUInt64LE(raf)
                if (chunkSize < 24 || position + chunkSize > headerSize) break
                if (guid.contentEquals(ASF_FILE_PROPERTIES_GUID)) {
                    raf.skipBytes(16) // File ID GUID inside the object data
                    return readUInt64LE(raf)
                }
                position += chunkSize
            }
            error("${file.name}: File Properties object not found")
        }
    }

    private fun readUInt64LE(raf: RandomAccessFile): Long {
        val b = ByteArray(8)
        raf.readFully(b)
        return (b[0].toUByte().toLong()) or
            (b[1].toUByte().toLong() shl 8) or
            (b[2].toUByte().toLong() shl 16) or
            (b[3].toUByte().toLong() shl 24) or
            (b[4].toUByte().toLong() shl 32) or
            (b[5].toUByte().toLong() shl 40) or
            (b[6].toUByte().toLong() shl 48) or
            (b[7].toUByte().toLong() shl 56)
    }

    companion object {
        private val ASF_HEADER_GUID = byteArrayOf(
            0x30, 0x26, 0xb2.toByte(), 0x75, 0x8e.toByte(), 0x66, 0xcf.toByte(), 0x11,
            0xa6.toByte(), 0xd9.toByte(), 0x00, 0xaa.toByte(), 0x00, 0x62, 0xce.toByte(), 0x6c,
        )
        private val ASF_FILE_PROPERTIES_GUID = byteArrayOf(
            0xA1.toByte(), 0xDC.toByte(), 0xAB.toByte(), 0x8C.toByte(), 0x47, 0xA9.toByte(), 0xCF.toByte(), 0x11,
            0x8E.toByte(), 0xE4.toByte(), 0x00, 0xC0.toByte(), 0x0C, 0x20, 0x53, 0x65,
        )
    }
}
