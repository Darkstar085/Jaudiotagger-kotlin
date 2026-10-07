package org.jaudiotagger.kt.audio.mp4

import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.decodeLatin1
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.io.readUInt16BE
import org.jaudiotagger.kt.tag.mp4.Mp4Item
import org.jaudiotagger.kt.tag.mp4.Mp4Tag

/**
 * Reads the ilst metadata box. Each item atom contains one or more `data`
 * atoms (version/flags + 4-byte locale + payload); "----" reverse-DNS items
 * additionally carry `mean` (issuer) and `name` (identifier) atoms.
 */
internal object Mp4TagReader {

    private const val TYPE_IMPLICIT = 0
    private const val TYPE_TEXT = 1
    private const val TYPE_JPEG = 13
    private const val TYPE_PNG = 14

    fun read(io: FileIo): Mp4Tag {
        val tag = Mp4Tag()
        val ilst = Mp4Atoms.findPath(io, "moov", "udta", "meta", "ilst")
            ?: Mp4Atoms.findPath(io, "moov", "meta", "ilst")
            ?: return tag

        Mp4Atoms.forEachChild(io, ilst.dataStart, ilst.dataEnd) { item ->
            parseItem(io, item, tag)
        }
        return tag
    }

    private fun parseItem(io: FileIo, item: Mp4Atoms.AtomInfo, tag: Mp4Tag) {
        if (item.dataLength <= 0) return
        io.position = item.dataStart
        val content = io.readFully(item.dataLength.toInt())

        if (item.id == "----") {
            parseReverseDns(content, tag)
            return
        }

        // walk the data atoms inside the item (covr may contain several)
        var pos = 0
        var parsedAny = false
        while (pos + 8 <= content.size) {
            val size = content.readInt32BE(pos)
            val boxId = content.decodeLatin1(pos + 4, pos + 8)
            if (size < 8 || pos + size > content.size) break
            if (boxId != "data") {
                pos += size
                continue
            }
            val type = content.readInt32BE(pos + 8) and 0xFFFFFF
            val payloadStart = pos + 16 // header + type/flags + locale
            val payload = content.copyOfRange(payloadStart, pos + size)

            when {
                item.id == "trkn" || item.id == "disk" -> {
                    if (payload.size >= 6) {
                        tag.items += Mp4Item.NumberPair(
                            item.id,
                            number = payload.readUInt16BE(2),
                            total = payload.readUInt16BE(4),
                        )
                    }
                }

                item.id == "gnre" -> {
                    if (payload.size >= 2) {
                        tag.items += Mp4Item.Genre(payload.readUInt16BE(0))
                    }
                }

                type == TYPE_JPEG || type == TYPE_PNG || item.id == "covr" ->
                    tag.items += Mp4Item.Cover(payload, isPng = type == TYPE_PNG)

                type == TYPE_TEXT ->
                    tag.items += Mp4Item.Text(item.id, payload.decodeToString())

                type == TYPE_IMPLICIT || type == 21 || type == 22 -> {
                    // integer-ish payloads exposed as text (tmpo, cpil, rtng...)
                    var value = 0L
                    for (b in payload) value = (value shl 8) or (b.toLong() and 0xFF)
                    tag.items += Mp4Item.Text(item.id, value.toString())
                }

                else -> tag.items += Mp4Item.Binary(item.id, content)
            }
            parsedAny = true
            pos += size
        }
        if (!parsedAny) {
            tag.items += Mp4Item.Binary(item.id, content)
        }
    }

    private fun parseReverseDns(content: ByteArray, tag: Mp4Tag) {
        var issuer = ""
        var identifier = ""
        var value = ""
        var pos = 0
        while (pos + 8 <= content.size) {
            val size = content.readInt32BE(pos)
            val boxId = content.decodeLatin1(pos + 4, pos + 8)
            if (size < 8 || pos + size > content.size) break
            when (boxId) {
                // mean/name payloads start with 4 bytes of version/flags
                "mean" -> issuer = content.decodeToString(pos + 12, pos + size)
                "name" -> identifier = content.decodeToString(pos + 12, pos + size)
                "data" -> value = content.decodeToString(pos + 16, pos + size)
            }
            pos += size
        }
        if (identifier.isNotEmpty()) {
            tag.items += Mp4Item.ReverseDns(issuer, identifier, value)
        }
    }
}
