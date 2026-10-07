# Jaudiotagger

Jaudiotagger is a Java library for reading and writing metadata in audio files.

It provides a common tagging API with format-specific implementations for formats
such as MP3, FLAC, Ogg Vorbis, MP4, AIFF, WAV, WMA, and DSF.

## Requirements

- Java 8 or newer for the current Maven build
- Maven for building and testing

## Build

Run:

```bash
mvn clean test
```

To build the library:

```bash
mvn clean package
```

## Project layout

- `src` — library source code
- `srctest` — unit and integration tests
- `testdata` — audio files used by tests
- `testtagdata` — metadata fixtures used by tests
- `pom.xml` — Maven build configuration
- `license.txt` — project license

## License

Jaudiotagger is distributed under the GNU Lesser General Public License.
See `license.txt` for details.
