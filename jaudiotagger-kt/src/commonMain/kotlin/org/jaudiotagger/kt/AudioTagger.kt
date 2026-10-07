package org.jaudiotagger.kt

import kotlinx.io.files.Path
import org.jaudiotagger.kt.audio.aiff.AiffFile
import org.jaudiotagger.kt.audio.ape.ApeTagIo
import org.jaudiotagger.kt.audio.asf.AsfFile
import org.jaudiotagger.kt.audio.dff.DffFileReader
import org.jaudiotagger.kt.audio.dsf.DsfFile
import org.jaudiotagger.kt.audio.flac.FlacInfoReader
import org.jaudiotagger.kt.audio.flac.FlacTagReader
import org.jaudiotagger.kt.audio.flac.FlacTagWriter
import org.jaudiotagger.kt.audio.monkey.MonkeyInfoReader
import org.jaudiotagger.kt.audio.mp3.Mp3InfoReader
import org.jaudiotagger.kt.audio.mp4.Mp4InfoReader
import org.jaudiotagger.kt.audio.mp4.Mp4TagReader
import org.jaudiotagger.kt.audio.mp4.Mp4TagWriter
import org.jaudiotagger.kt.audio.ogg.OggFile
import org.jaudiotagger.kt.audio.real.RealFileReader
import org.jaudiotagger.kt.audio.wav.WavInfoReader
import org.jaudiotagger.kt.audio.wav.WavTagIo
import org.jaudiotagger.kt.audio.wavpack.WavPackInfoReader
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.withFileIo
import org.jaudiotagger.kt.tag.Tag
import org.jaudiotagger.kt.tag.ape.ApeTag
import org.jaudiotagger.kt.tag.asf.AsfTag
import org.jaudiotagger.kt.tag.flac.FlacTag
import org.jaudiotagger.kt.tag.generic.GenericTag
import org.jaudiotagger.kt.tag.id3.Id3v1Tag
import org.jaudiotagger.kt.tag.id3.Id3v1TagIo
import org.jaudiotagger.kt.tag.id3.Id3v2Tag
import org.jaudiotagger.kt.tag.id3.Id3v2TagReader
import org.jaudiotagger.kt.tag.id3.Id3v2TagWriter
import org.jaudiotagger.kt.tag.id3.Id3v2Version
import org.jaudiotagger.kt.tag.mp4.Mp4Tag
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.kt.tag.wav.WavTag

/**
 * Audio formats supported by the library.
 */
enum class AudioFormat(vararg val extensions: String) {
    FLAC("flac"),

    /** Ogg container with a Vorbis or Opus stream; the codec is detected from the first packet. */
    OGG("ogg", "oga", "opus"),
    APE("ape"),
    WAVPACK("wv"),

    MP3("mp3"),

    /** DSD Stream File; carries an ID3v2 tag at the end of the file. */
    DSF("dsf"),

    /** DSDIFF. Read-only (the original library had no writer either). */
    DFF("dff"),

    WAV("wav"),

    AIFF("aif", "aiff", "aifc"),

    MP4("m4a", "mp4", "m4p", "m4b"),

    WMA("wma"),

    /** Read-only. */
    REALAUDIO("ra", "rm");

    companion object {
        fun fromExtension(extension: String): AudioFormat? {
            val lower = extension.lowercase()
            return entries.firstOrNull { lower in it.extensions }
        }

        fun forPath(path: Path): AudioFormat {
            val name = path.name
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            return fromExtension(extension)
                ?: throw CannotReadException("No Reader associated with this extension:$extension")
        }
    }
}

/**
 * Parsed audio file: technical properties plus (mutable) tag.
 */
class AudioFile(
    val format: AudioFormat,
    val properties: AudioProperties,
    val tag: Tag,
)

/**
 * Entry point for reading and writing audio metadata.
 *
 * Two API levels:
 *  - [Path]-based convenience functions;
 *  - [FileIo]-based functions for callers that own the file handle — e.g. an
 *    Android `FileDescriptor` obtained from MediaStore/SAF, avoiding temp copies.
 */
object AudioTagger {

    fun read(path: Path): AudioFile {
        val format = AudioFormat.forPath(path)
        return withFileIo(path, readOnly = true) { read(it, format) }
    }

    fun read(io: FileIo, format: AudioFormat): AudioFile = when (format) {
        AudioFormat.FLAC -> AudioFile(
            format = format,
            properties = FlacInfoReader.read(io),
            tag = FlacTagReader.read(io),
        )

        AudioFormat.OGG -> AudioFile(
            format = format,
            properties = OggFile.readProperties(io),
            tag = OggFile.readTag(io),
        )

        AudioFormat.APE -> AudioFile(
            format = format,
            properties = MonkeyInfoReader.read(io),
            tag = ApeTagIo.readTag(io),
        )

        AudioFormat.WAVPACK -> AudioFile(
            format = format,
            properties = WavPackInfoReader.read(io),
            tag = ApeTagIo.readTag(io),
        )

        AudioFormat.REALAUDIO -> AudioFile(
            format = format,
            properties = RealFileReader.readProperties(io),
            tag = RealFileReader.readTag(io),
        )

        AudioFormat.MP3 -> AudioFile(
            format = format,
            properties = Mp3InfoReader.read(io),
            // ID3v2 is the primary tag; fall back to a trailing ID3v1
            tag = Id3v2TagReader.read(io) ?: Id3v1TagIo.read(io) ?: Id3v1Tag(),
        )

        AudioFormat.DSF -> AudioFile(
            format = format,
            properties = DsfFile.readProperties(io),
            tag = DsfFile.readTag(io) ?: Id3v2Tag(Id3v2Version.V24),
        )

        AudioFormat.DFF -> AudioFile(
            format = format,
            properties = DffFileReader.readProperties(io),
            tag = DffFileReader.readTag(io) ?: Id3v2Tag(Id3v2Version.V24),
        )

        AudioFormat.WAV -> AudioFile(
            format = format,
            properties = WavInfoReader.read(io),
            tag = WavTagIo.readTag(io),
        )

        AudioFormat.AIFF -> AudioFile(
            format = format,
            properties = AiffFile.readProperties(io),
            tag = AiffFile.readTag(io) ?: Id3v2Tag(Id3v2Version.V23),
        )

        AudioFormat.MP4 -> AudioFile(
            format = format,
            properties = Mp4InfoReader.read(io),
            tag = Mp4TagReader.read(io),
        )

        AudioFormat.WMA -> AudioFile(
            format = format,
            properties = AsfFile.readProperties(io),
            tag = AsfFile.readTag(io),
        )
    }

    fun readProperties(path: Path): AudioProperties {
        val format = AudioFormat.forPath(path)
        return withFileIo(path, readOnly = true) { readProperties(it, format) }
    }

    fun readProperties(io: FileIo, format: AudioFormat): AudioProperties = when (format) {
        AudioFormat.FLAC -> FlacInfoReader.read(io)
        AudioFormat.OGG -> OggFile.readProperties(io)
        AudioFormat.APE -> MonkeyInfoReader.read(io)
        AudioFormat.WAVPACK -> WavPackInfoReader.read(io)
        AudioFormat.REALAUDIO -> RealFileReader.readProperties(io)
        AudioFormat.MP3 -> Mp3InfoReader.read(io)
        AudioFormat.DSF -> DsfFile.readProperties(io)
        AudioFormat.DFF -> DffFileReader.readProperties(io)
        AudioFormat.WAV -> WavInfoReader.read(io)
        AudioFormat.AIFF -> AiffFile.readProperties(io)
        AudioFormat.MP4 -> Mp4InfoReader.read(io)
        AudioFormat.WMA -> AsfFile.readProperties(io)
    }

    /** Writes [tag] into the file at [path] in place. */
    fun write(path: Path, tag: Tag) {
        val format = AudioFormat.forPath(path)
        withFileIo(path, readOnly = false) { write(it, tag, format) }
    }

    /** Writes [tag] through an existing writable handle. */
    fun write(io: FileIo, tag: Tag, format: AudioFormat) {
        when (format) {
            AudioFormat.FLAC -> FlacTagWriter.write(
                io,
                tag as? FlacTag
                    ?: throw CannotWriteException("FLAC file requires a FlacTag, got ${tag::class.simpleName}"),
            )

            AudioFormat.OGG -> OggFile.writeTag(
                io,
                tag as? VorbisCommentTag
                    ?: throw CannotWriteException("OGG file requires a VorbisCommentTag, got ${tag::class.simpleName}"),
            )

            AudioFormat.APE, AudioFormat.WAVPACK -> ApeTagIo.writeTag(
                io,
                tag as? ApeTag
                    ?: throw CannotWriteException("${format.name} file requires an ApeTag, got ${tag::class.simpleName}"),
            )

            AudioFormat.REALAUDIO ->
                throw CannotWriteException("RealAudio is read-only")

            AudioFormat.DFF ->
                throw CannotWriteException("DFF is read-only")

            AudioFormat.MP3 -> when (tag) {
                is Id3v2Tag -> Id3v2TagWriter.write(io, tag)
                is Id3v1Tag -> Id3v1TagIo.write(io, tag)
                else -> throw CannotWriteException(
                    "MP3 file requires an Id3v2Tag or Id3v1Tag, got ${tag::class.simpleName}"
                )
            }

            AudioFormat.DSF -> DsfFile.writeTag(
                io,
                tag as? Id3v2Tag
                    ?: throw CannotWriteException("DSF file requires an Id3v2Tag, got ${tag::class.simpleName}"),
            )

            AudioFormat.WAV -> WavTagIo.writeTag(
                io,
                tag as? WavTag
                    ?: throw CannotWriteException("WAV file requires a WavTag, got ${tag::class.simpleName}"),
            )

            AudioFormat.AIFF -> AiffFile.writeTag(
                io,
                tag as? Id3v2Tag
                    ?: throw CannotWriteException("AIFF file requires an Id3v2Tag, got ${tag::class.simpleName}"),
            )

            AudioFormat.MP4 -> Mp4TagWriter.write(
                io,
                tag as? Mp4Tag
                    ?: throw CannotWriteException("MP4 file requires an Mp4Tag, got ${tag::class.simpleName}"),
            )

            AudioFormat.WMA -> AsfFile.writeTag(
                io,
                tag as? AsfTag
                    ?: throw CannotWriteException("WMA file requires an AsfTag, got ${tag::class.simpleName}"),
            )
        }
    }

    /** Removes the tag (metadata fields and artwork) from the file. */
    fun deleteTag(path: Path) {
        val format = AudioFormat.forPath(path)
        withFileIo(path, readOnly = false) { deleteTag(it, format) }
    }

    fun deleteTag(io: FileIo, format: AudioFormat) {
        when (format) {
            AudioFormat.FLAC -> FlacTagWriter.delete(io)
            AudioFormat.OGG -> OggFile.deleteTag(io)
            AudioFormat.APE, AudioFormat.WAVPACK -> ApeTagIo.deleteTag(io)
            AudioFormat.REALAUDIO -> throw CannotWriteException("RealAudio is read-only")
            AudioFormat.MP3 -> {
                // remove both tag flavours, like the original AudioFileIO.delete
                Id3v1TagIo.delete(io)
                Id3v2TagWriter.delete(io)
            }

            AudioFormat.DSF -> DsfFile.deleteTag(io)
            AudioFormat.DFF -> throw CannotWriteException("DFF is read-only")
            AudioFormat.WAV -> WavTagIo.deleteTag(io)
            AudioFormat.AIFF -> AiffFile.deleteTag(io)
            AudioFormat.MP4 -> Mp4TagWriter.delete(io)
            AudioFormat.WMA -> AsfFile.deleteTag(io)
        }
    }

    /** Creates an empty tag of the right type for [format]. */
    fun createTag(format: AudioFormat): Tag = when (format) {
        AudioFormat.FLAC -> FlacTag()
        AudioFormat.OGG -> VorbisCommentTag()
        AudioFormat.APE, AudioFormat.WAVPACK -> ApeTag()
        AudioFormat.REALAUDIO -> GenericTag()
        AudioFormat.MP3 -> Id3v2Tag(Id3v2Version.V23)
        AudioFormat.DSF -> Id3v2Tag(Id3v2Version.V24)
        AudioFormat.DFF -> Id3v2Tag(Id3v2Version.V24)
        AudioFormat.WAV -> WavTag()
        AudioFormat.AIFF -> Id3v2Tag(Id3v2Version.V23)
        AudioFormat.MP4 -> Mp4Tag()
        AudioFormat.WMA -> AsfTag()
    }
}
