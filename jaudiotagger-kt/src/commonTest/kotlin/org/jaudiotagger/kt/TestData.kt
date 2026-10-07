package org.jaudiotagger.kt

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

/** Directory with the repo's audio samples; set by Gradle via TESTDATA_DIR. */
expect fun testDataDir(): String

fun testDataPath(name: String): Path = Path(testDataDir(), name)

/** Copies a testdata file into a temp location so write tests do not mutate samples. */
fun copyToTemp(name: String, prefix: String): Path {
    val source = testDataPath(name)
    val target = Path(SystemTemporaryDirectory, "$prefix-$name")
    SystemFileSystem.source(source).buffered().use { input ->
        SystemFileSystem.sink(target).buffered().use { output ->
            input.transferTo(output)
        }
    }
    return target
}
