package org.jaudiotagger.kt

actual fun testDataDir(): String =
    System.getenv("TESTDATA_DIR") ?: "../testdata"
