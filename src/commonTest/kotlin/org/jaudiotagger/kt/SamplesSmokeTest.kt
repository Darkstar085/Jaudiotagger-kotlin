package org.jaudiotagger.kt

import kotlinx.io.files.SystemFileSystem
import org.jaudiotagger.kt.tag.FieldKey
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Smoke test: every FLAC/OGG sample in testdata must read, and survive a
 * write → re-read round trip without losing existing fields.
 */
class SamplesSmokeTest {

    private val samples = listOf(
        "test.flac",
        "test2.flac",
        "test3.flac",
        "test.ogg",
        "test3.ogg",
        "test5.ogg",
        "test76.ogg",
        "test77.ogg",
        "testlargeimage.ogg",
        "testsmallimage.ogg",
        "test-opus.opus",
        "test-opus-padding.opus",
        "test-opus-binary-tail.opus",
        "test-opus-in-ogg.ogg",
        "test-opus-track-total.opus",
    )

    // corrupt on purpose; the original library throws CannotReadException on them
    // (Issue178Test for test36.ogg, Issue183Test for test508.ogg)
    private val corruptSamples = listOf("test36.ogg", "test508.ogg")

    @Test
    fun readAndRewriteAllSamples() {
        val failures = mutableListOf<String>()
        for (sample in samples) {
            if (SystemFileSystem.metadataOrNull(testDataPath(sample)) == null) continue
            try {
                val path = copyToTemp(sample, "smoke")
                val file = AudioTagger.read(path)
                val artistBefore = file.tag.first(FieldKey.ARTIST)

                file.tag.set(FieldKey.COMMENT, "smoke test")
                AudioTagger.write(path, file.tag)

                val reread = AudioTagger.read(path)
                check(reread.tag.first(FieldKey.COMMENT) == "smoke test") { "comment not written" }
                check(reread.tag.first(FieldKey.ARTIST) == artistBefore) { "artist lost on rewrite" }
                check(reread.properties.sampleRate == file.properties.sampleRate) { "properties changed" }

                SystemFileSystem.delete(path)
            } catch (e: Throwable) {
                failures += "$sample: ${e.message}"
            }
        }
        assertTrue(failures.isEmpty(), "Failures:\n${failures.joinToString("\n")}")
    }

    @Test
    fun corruptSamplesFailWithCannotRead() {
        for (sample in corruptSamples) {
            if (SystemFileSystem.metadataOrNull(testDataPath(sample)) == null) continue
            assertFailsWith<CannotReadException>(sample) {
                AudioTagger.read(testDataPath(sample))
            }
        }
    }
}
