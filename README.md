# Jaudiotagger

Jaudiotagger is an audio metadata library with a Kotlin Multiplatform implementation.

## Kotlin Multiplatform

The `jaudiotagger-kt` module is the active library implementation for Android, JVM, iOS, and macOS. Format logic is shared through `commonMain`, with platform-specific file I/O behind `FileIo`.

Supported formats include MP3, FLAC, Ogg Vorbis, Ogg Opus, MP4/M4A, WAV, AIFF, WMA/ASF, Monkey's Audio, WavPack, DSF, DFF, and RealAudio.

### Build

Run the JVM test suite:

```bash
./gradlew :jaudiotagger-kt:jvmTest
```

Build the Android artifact:

```bash
./gradlew :jaudiotagger-kt:assembleRelease
```

Verify the common code remains JVM-free:

```bash
./gradlew :jaudiotagger-kt:compileKotlinMacosArm64
```

### Kotlin usage

```kotlin
import kotlinx.io.files.Path
import org.jaudiotagger.kt.AudioTagger
import org.jaudiotagger.kt.tag.FieldKey

val file = AudioTagger.read(Path("/music/track.flac"))
println(file.properties.duration)
println(file.tag.first(FieldKey.ARTIST))

file.tag.set(FieldKey.TITLE, "New title")
AudioTagger.write(Path("/music/track.flac"), file.tag)
```

The implementation avoids runtime reflection and routes file access through `FileIo`, allowing Android MediaStore/SAF files to be edited in place through `FileDescriptorIo`.

## Project layout

- `jaudiotagger-kt/src/commonMain` — shared Kotlin implementation
- `jaudiotagger-kt/src/androidMain` — Android-specific I/O
- `jaudiotagger-kt/src/jvmMain` — JVM-specific I/O
- `jaudiotagger-kt/src/nativeMain` — native I/O
- `jaudiotagger-kt/src/*Test` — Kotlin regression and platform tests
- `testdata/` — audio samples used by the Kotlin test suite
- `.github/workflows/manual-test.yml` — manual Kotlin verification workflow

The repository is Kotlin-first; the legacy Java implementation and Maven build have been removed.

## License

Jaudiotagger is distributed under the GNU Lesser General Public License 2.1.
See `license.txt`.
