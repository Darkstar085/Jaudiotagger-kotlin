# Jaudiotagger

Jaudiotagger is an audio metadata library with a Kotlin Multiplatform implementation.

## Kotlin Multiplatform

The `jaudiotagger-kt` module provides the migrated Kotlin implementation for Android, JVM, iOS, and macOS. The format logic is shared through `commonMain`, with platform-specific random-access I/O behind `FileIo`.

Supported formats include MP3, FLAC, Ogg Vorbis, Ogg Opus, MP4/M4A, WAV, AIFF, WMA/ASF, Monkey's Audio, WavPack, DSF, DFF, and RealAudio.

### Build

Run the Kotlin test suite:

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

The Kotlin port avoids runtime reflection and routes file access through `FileIo`, allowing Android MediaStore/SAF files to be edited in place through `FileDescriptorIo`.

## Java reference implementation

The original Java implementation remains under `src/` as the compatibility and porting reference. Its Maven build and test suite are retained.

Run the Java tests with:

```bash
mvn clean test
```

See `jaudiotagger-kt/PORTING.md` for the format-by-format migration status, intentional Kotlin differences, and known fixes.

## License

Jaudiotagger is distributed under the GNU Lesser General Public License 2.1.
See `license.txt`.
