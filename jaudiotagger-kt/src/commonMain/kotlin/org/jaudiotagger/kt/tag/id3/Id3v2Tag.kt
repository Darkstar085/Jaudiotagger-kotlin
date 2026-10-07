package org.jaudiotagger.kt.tag.id3

import org.jaudiotagger.kt.tag.Artwork
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.PictureTypes
import org.jaudiotagger.kt.tag.Tag

enum class Id3v2Version(val major: Int) {
    V22(2),
    V23(3),
    V24(4);

    internal val frameKeys: Map<FieldKey, Id3FrameKey>
        get() = when (this) {
            V22 -> id3v22FrameKeys
            V23 -> id3v23FrameKeys
            V24 -> id3v24FrameKeys
        }
}

/**
 * ID3v2 tag: an ordered list of frames, addressed through [FieldKey] via the
 * per-version frame mappings. Unknown frames are preserved as raw bytes.
 */
class Id3v2Tag(val version: Id3v2Version) : Tag {

    val frames = mutableListOf<Id3v2Frame>()

    private fun key(fieldKey: FieldKey): Id3FrameKey =
        version.frameKeys[fieldKey]
            ?: throw IllegalArgumentException("FieldKey $fieldKey is not supported by ID3${version.name}")

    private fun numberTotalFrame(frameId: String): Id3v2Frame.NumberTotal? {
        val frame = frames.filterIsInstance<Id3v2Frame.NumberTotal>().firstOrNull { it.id == frameId }
        if (frame != null) return frame
        // legacy: TRCK/TPOS read before NumberTotal support
        val text = frames.filterIsInstance<Id3v2Frame.Text>().firstOrNull { it.id == frameId } ?: return null
        val part = PartOfSetValue(text.values.firstOrNull() ?: "")
        val migrated = Id3v2Frame.NumberTotal(frameId, part)
        frames[frames.indexOf(text)] = migrated
        return migrated
    }

    private fun valuesOf(k: Id3FrameKey): List<String> = frames.mapNotNull { frame ->
        when {
            frame.id != k.frameId -> null
            frame is Id3v2Frame.UserText && k.subId != null ->
                if (frame.description.equals(k.subId, ignoreCase = true)) frame.value else null

            frame is Id3v2Frame.LanguageText ->
                if ((k.subId ?: "") == frame.description) frame.text else null

            frame is Id3v2Frame.PairedText && k.subId != null ->
                frame.pairs.filter { it.first.equals(k.subId, ignoreCase = true) }
                    .joinToString("^@") { it.second }
                    .ifEmpty { null }

            frame is Id3v2Frame.UserUrl && k.subId != null ->
                if (frame.description.equals(k.subId, ignoreCase = true)) frame.url else null

            frame is Id3v2Frame.Url -> frame.url
            frame is Id3v2Frame.UniqueFileId ->
                if (k.subId == null || frame.owner.equals(k.subId, ignoreCase = true)) {
                    frame.data.decodeToString()
                } else null

            frame is Id3v2Frame.Popularimeter -> "${frame.rating}"
            frame is Id3v2Frame.Text -> return@mapNotNull null // handled below for multi-values
            else -> null
        }
    } + frames.filterIsInstance<Id3v2Frame.Text>().filter { it.id == k.frameId }.flatMap { it.values }

    override fun first(key: FieldKey): String? = all(key).firstOrNull()

    override fun all(key: FieldKey): List<String> {
        if (isNumberFieldKey(key)) {
            val frame = numberTotalFrame(key(key).frameId) ?: return emptyList()
            val count = frame.part.getCount() ?: return emptyList()
            if (count == 0) return emptyList()
            return frame.part.getCountAsText()?.let { listOf(it) } ?: emptyList()
        }
        if (isTotalFieldKey(key)) {
            val frame = numberTotalFrame(key(key).frameId) ?: return emptyList()
            val total = frame.part.getTotal() ?: return emptyList()
            if (total == 0) return emptyList()
            return frame.part.getTotalAsText()?.let { listOf(it) } ?: emptyList()
        }
        if (key == FieldKey.COMMENT) return orderedLanguageTextValues(userCommentFrames())
        if (key == FieldKey.LYRICS) return orderedLanguageTextValues(userLyricsFrames())
        val k = key(key)
        val values = valuesOf(k)
        return if (key == FieldKey.GENRE) values.map(::decodeGenre) else values
    }

    override fun set(key: FieldKey, value: String) {
        when {
            isNumberFieldKey(key) || isTotalFieldKey(key) -> setNumberTotal(key, value)
            key == FieldKey.COMMENT -> {
                removeUserCommentFrames()
                frames += Id3v2Frame.LanguageText(commentFrameId(), "eng", "", value)
            }
            key == FieldKey.LYRICS -> {
                removeUserLyricsFrames()
                frames += Id3v2Frame.LanguageText(lyricsFrameId(), "eng", "", value)
            }
            else -> {
                remove(key)
                add(key, value)
            }
        }
    }

    override fun add(key: FieldKey, value: String) {
        when {
            isNumberFieldKey(key) || isTotalFieldKey(key) -> setNumberTotal(key, value)
            key == FieldKey.COMMENT ->
                frames += Id3v2Frame.LanguageText(commentFrameId(), "eng", "", value)
            key == FieldKey.LYRICS ->
                frames += Id3v2Frame.LanguageText(lyricsFrameId(), "eng", "", value)
            else -> addField(key, value)
        }
    }

    private fun setNumberTotal(key: FieldKey, value: String) {
        val k = key(key)
        val existing = numberTotalFrame(k.frameId)
        if (existing != null) {
            if (isNumberFieldKey(key)) existing.part.setCount(value)
            else existing.part.setTotal(value)
            return
        }
        val part = PartOfSetValue()
        if (isNumberFieldKey(key)) part.setCount(value) else part.setTotal(value)
        frames += Id3v2Frame.NumberTotal(k.frameId, part)
    }

    private fun addField(key: FieldKey, value: String) {
        val k = key(key)
        when {
            k.subId != null && k.frameId.startsWith("TX") ->
                frames += Id3v2Frame.UserText(k.frameId, k.subId, value)

            k.subId != null && k.frameId.startsWith("WX") ->
                frames += Id3v2Frame.UserUrl(k.frameId, k.subId, value)

            k.frameId == "COMM" || k.frameId == "COM" || k.frameId == "USLT" || k.frameId == "ULT" ->
                frames += Id3v2Frame.LanguageText(k.frameId, "eng", k.subId ?: "", value)

            k.subId != null && (k.frameId == "IPLS" || k.frameId == "TIPL" || k.frameId == "IPL") -> {
                val existing = frames.filterIsInstance<Id3v2Frame.PairedText>()
                    .firstOrNull { it.id == k.frameId }
                if (existing != null) {
                    existing.pairs += k.subId to value
                } else {
                    frames += Id3v2Frame.PairedText(k.frameId, mutableListOf(k.subId to value))
                }
            }

            k.frameId.startsWith("W") -> frames += Id3v2Frame.Url(k.frameId, value)

            else -> {
                val existing = frames.filterIsInstance<Id3v2Frame.Text>()
                    .firstOrNull { it.id == k.frameId }
                if (existing != null) {
                    existing.values += value
                } else {
                    frames += Id3v2Frame.Text(k.frameId, mutableListOf(value))
                }
            }
        }
    }

    override fun remove(key: FieldKey) {
        when {
            key == FieldKey.TRACK -> deleteNumberTotalFrame(key, FieldKey.TRACK_TOTAL, deleteNumber = true)
            key == FieldKey.TRACK_TOTAL -> deleteNumberTotalFrame(key, FieldKey.TRACK, deleteNumber = false)
            key == FieldKey.DISC_NO -> deleteNumberTotalFrame(key, FieldKey.DISC_TOTAL, deleteNumber = true)
            key == FieldKey.DISC_TOTAL -> deleteNumberTotalFrame(key, FieldKey.DISC_NO, deleteNumber = false)
            key == FieldKey.MOVEMENT_NO -> deleteNumberTotalFrame(key, FieldKey.MOVEMENT_TOTAL, deleteNumber = true)
            key == FieldKey.MOVEMENT_TOTAL -> deleteNumberTotalFrame(key, FieldKey.MOVEMENT_NO, deleteNumber = false)
            key == FieldKey.COMMENT -> removeUserCommentFrames()
            key == FieldKey.LYRICS -> removeUserLyricsFrames()
            else -> {
                val k = key(key)
                frames.removeAll { frame ->
                    frame.id == k.frameId && when {
                        frame is Id3v2Frame.UserText && k.subId != null ->
                            frame.description.equals(k.subId, ignoreCase = true)

                        frame is Id3v2Frame.UserUrl && k.subId != null ->
                            frame.description.equals(k.subId, ignoreCase = true)

                        frame is Id3v2Frame.LanguageText -> (k.subId ?: "") == frame.description
                        k.subId == null -> true
                        else -> false
                    }
                }
            }
        }
    }

    private fun deleteNumberTotalFrame(
        key: FieldKey,
        otherKey: FieldKey,
        deleteNumber: Boolean,
    ) {
        val frameId = key(key).frameId
        val frame = numberTotalFrame(frameId) ?: return
        val otherValue = first(otherKey)
        if (otherValue.isNullOrEmpty()) {
            frames.remove(frame)
            return
        }
        if (deleteNumber) {
            if (frame.part.getTotal() == 0) {
                frames.remove(frame)
            } else {
                frame.part.setCount(0)
            }
        } else {
            if (frame.part.getCount() == 0 || frame.part.getCount() == null) {
                frames.remove(frame)
            } else {
                frame.part.setTotal(0)
            }
        }
    }

    override val fieldCount: Int get() = frames.size

    override val isEmpty: Boolean get() = frames.isEmpty()

    override fun clear() {
        frames.clear()
    }

    override val artworks: List<Artwork>
        get() = frames.filterIsInstance<Id3v2Frame.Picture>().map {
            Artwork(
                data = it.data,
                mimeType = it.mimeType,
                description = it.description,
                pictureType = it.pictureType,
            )
        }

    override fun addArtwork(artwork: Artwork) {
        val frameId = if (version == Id3v2Version.V22) "PIC" else "APIC"
        frames += Id3v2Frame.Picture(
            id = frameId,
            mimeType = artwork.mimeType,
            pictureType = artwork.pictureType,
            description = artwork.description,
            data = artwork.data,
        )
    }

    override fun clearArtworks() {
        frames.removeAll { it is Id3v2Frame.Picture }
    }

    override fun toString(): String = "Id3v2Tag(${version.name}, frames=$frames)"

    private fun commentFrameId(): String = if (version == Id3v2Version.V22) "COM" else "COMM"

    private fun lyricsFrameId(): String = if (version == Id3v2Version.V22) "ULT" else "USLT"

    private fun isUserCommentFrame(frame: Id3v2Frame.LanguageText): Boolean =
        frame.id == commentFrameId() && !frame.description.startsWith("iTun", ignoreCase = true)

    private fun userCommentFrames(): List<Id3v2Frame.LanguageText> =
        frames.filterIsInstance<Id3v2Frame.LanguageText>().filter(::isUserCommentFrame)

    private fun userLyricsFrames(): List<Id3v2Frame.LanguageText> =
        frames.filterIsInstance<Id3v2Frame.LanguageText>().filter { it.id == lyricsFrameId() }

    private fun orderedLanguageTextValues(frames: List<Id3v2Frame.LanguageText>): List<String> {
        val (emptyDescription, described) = frames.partition { it.description.isEmpty() }
        return (emptyDescription + described).map { it.text }
    }

    private fun removeUserCommentFrames() {
        frames.removeAll { it is Id3v2Frame.LanguageText && isUserCommentFrame(it) }
    }

    private fun removeUserLyricsFrames() {
        frames.removeAll { it is Id3v2Frame.LanguageText && it.id == lyricsFrameId() }
    }

    private fun decodeGenre(value: String): String {
        val trimmed = value.trim()
        val refMatch = Regex("^\\((\\d+)\\)(.*)").find(trimmed)
        if (refMatch != null) {
            val rest = refMatch.groupValues[2]
            if (rest.isNotEmpty()) return rest
            return GenreTypes.nameOf(refMatch.groupValues[1].toInt()) ?: trimmed
        }
        trimmed.toIntOrNull()?.let { id -> return GenreTypes.nameOf(id) ?: trimmed }
        return when (trimmed) {
            "(RX)", "RX" -> "Remix"
            "(CR)", "CR" -> "Cover"
            else -> trimmed
        }
    }
}
