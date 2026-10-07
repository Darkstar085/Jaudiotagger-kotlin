package org.jaudiotagger.kt

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun testDataDir(): String =
    getenv("TESTDATA_DIR")?.toKString() ?: "../testdata"
