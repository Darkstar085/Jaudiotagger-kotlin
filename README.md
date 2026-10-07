# Jaudiotagger Kotlin

**Kotlin Multiplatform audio metadata library** for reading, editing, and writing audio tags across Android, JVM, iOS, and macOS.

[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.darkstar085/jaudiotagger-kt)](https://central.sonatype.com/artifact/io.github.darkstar085/jaudiotagger-kt)
[![License](https://img.shields.io/badge/License-LGPL--2.1-blue.svg)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/Darkstar085/Jaudiotagger-kotlin?display_name=tag)](https://github.com/Darkstar085/Jaudiotagger-kotlin/releases)

> Read audio metadata. Change it. Write it back — from shared Kotlin code.

## ✨ Features

- Kotlin Multiplatform — Android, JVM, iOS, and macOS
- Shared metadata parsing and writing through `commonMain`
- Read and write audio tags and properties
- Android MediaStore / SAF support
- File-descriptor based Android I/O
- No runtime reflection
- Maven Central distribution

## 🚀 Installation

Jaudiotagger Kotlin is available from Maven Central.

```kotlin
dependencies {
    implementation("io.github.darkstar085:jaudiotagger-kt:0.1.0")
}
```

For Kotlin Multiplatform:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.darkstar085:jaudiotagger-kt:0.1.0")
        }
    }
}
```

## 🎵 Usage

```kotlin
import kotlinx.io.files.Path
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.FieldKey

val path = Path("/music/track.flac")
val file = AudioTagger.read(path)

println(file.properties.duration)
println(file.tag.first(FieldKey.ARTIST))

file.tag.set(FieldKey.TITLE, "New title")

AudioTagger.write(path, file.tag)
```

The typical workflow is:

**Read → inspect → modify → write**

## 📱 Android

Android-specific file access is separated from the shared metadata implementation.

`FileDescriptorIo` allows metadata operations on files accessed through Android file descriptors, making the library suitable for **MediaStore** and **Storage Access Framework (SAF)** based applications.

Android supports **API 21+**.

## 🌍 Supported Platforms

| Platform | Support |
| --- | :---: |
| Android | ✅ |
| JVM | ✅ |
| iOS | ✅ |
| iOS Simulator | ✅ |
| macOS ARM64 | ✅ |

## 🎧 Supported Formats

MP3 · FLAC · Ogg Vorbis · Ogg Opus · MP4/M4A · WAV · AIFF · WMA/ASF · APE · WavPack · DSF · DFF · RealAudio

## 🛠️ Build & Test

```bash
# Run JVM tests
./gradlew jvmTest

# Build Android AAR
./gradlew bundleAndroidMainAar

# Verify macOS target
./gradlew compileKotlinMacosArm64
```

## 📦 Publishing

Maven coordinates:

```text
io.github.darkstar085:jaudiotagger-kt:<version>
```

Releases are published to **Maven Central**, with the Android AAR also available as a GitHub Release asset.

[View Releases →](https://github.com/Darkstar085/Jaudiotagger-kotlin/releases)

## 📄 License

Jaudiotagger Kotlin is distributed under the **GNU Lesser General Public License v2.1**.

See [LICENSE](LICENSE) for the complete license text.

---

<p align="center">
  Made with Kotlin Multiplatform
</p>
