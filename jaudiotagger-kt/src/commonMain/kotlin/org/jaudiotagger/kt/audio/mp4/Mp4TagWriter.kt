package org.jaudiotagger.kt.audio.mp4

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.io.FileIo
import org.jaudiotagger.kt.io.ShiftData
import org.jaudiotagger.kt.io.decodeLatin1
import org.jaudiotagger.kt.io.int32BE
import org.jaudiotagger.kt.io.readFully
import org.jaudiotagger.kt.io.readInt32BE
import org.jaudiotagger.kt.tag.mp4.Mp4Item
import org.jaudiotagger.kt.tag.mp4.Mp4Tag

/**
 * Writes the ilst metadata box.
 *
 * The whole moov atom is parsed into an in-memory tree, the udta/meta/ilst
 * path is replaced (or created), and the tree is serialized back. A size
 * change is absorbed by a top-level `free` atom following moov when possible;
 * otherwise the file tail is shifted and every stco/co64 chunk offset pointing
 * behind the old moov end is patched accordingly.
 */
internal object Mp4TagWriter {

    private const val TYPE_IMPLICIT = 0
    private const val TYPE_TEXT = 1
    private const val TYPE_INTEGER = 21
    private const val TYPE_JPEG = 13
    private const val TYPE_PNG = 14
    private const val FREE_PADDING = 4000

    // ---- atom tree ----

    private sealed class Node(val id: String) {
        class Leaf(id: String, var data: ByteArray) : Node(id)

        /** [preamble] holds bytes between the header and the children (meta version/flags). */
        class Container(id: String, val preamble: ByteArray, val children: MutableList<Node>) : Node(id)
    }

    private val containerIds = setOf("udta", "meta", "trak", "mdia", "minf", "stbl")

    private fun parseChildren(data: ByteArray, from: Int, to: Int): MutableList<Node>? {
        val children = mutableListOf<Node>()
        var pos = from
        while (pos < to) {
            if (pos + 8 > to) return null
            val size = data.readInt32BE(pos).toUInt().toLong()
            if (size < 8 || pos + size > to) return null
            val id = data.decodeLatin1(pos + 4, pos + 8)
            val bodyStart = pos + 8
            val bodyEnd = (pos + size).toInt()
            children += parseNode(id, data, bodyStart, bodyEnd)
            pos = bodyEnd
        }
        return children
    }

    private fun parseNode(id: String, data: ByteArray, from: Int, to: Int): Node {
        if (id in containerIds) {
            val preambleLength = if (id == "meta") 4 else 0
            if (to - from >= preambleLength) {
                val children = parseChildren(data, from + preambleLength, to)
                if (children != null) {
                    return Node.Container(id, data.copyOfRange(from, from + preambleLength), children)
                }
            }
        }
        return Node.Leaf(id, data.copyOfRange(from, to))
    }

    private fun serializedSize(node: Node): Int = when (node) {
        is Node.Leaf -> 8 + node.data.size
        is Node.Container -> 8 + node.preamble.size + node.children.sumOf { serializedSize(it) }
    }

    private fun serialize(node: Node, out: Buffer) {
        out.write(int32BE(serializedSize(node)))
        out.write(latin1(node.id))
        when (node) {
            is Node.Leaf -> out.write(node.data)
            is Node.Container -> {
                out.write(node.preamble)
                node.children.forEach { serialize(it, out) }
            }
        }
    }

    private fun latin1(text: String): ByteArray =
        ByteArray(text.length) { (text[it].code and 0xFF).toByte() }

    private fun freeAtomHeader(size: Long): ByteArray = int32BE(size.toInt()) + latin1("free")

    private fun freeAtom(size: Int): ByteArray {
        val atom = ByteArray(size)
        freeAtomHeader(size.toLong()).copyInto(atom)
        return atom
    }

    // ---- ilst serialization ----

    private fun buildDataBox(type: Int, payload: ByteArray): ByteArray {
        val out = Buffer()
        out.write(int32BE(16 + payload.size))
        out.write(latin1("data"))
        out.write(int32BE(type))
        out.write(int32BE(0)) // locale
        out.write(payload)
        return out.readByteArray()
    }

    private fun buildItem(atomId: String, vararg boxes: ByteArray): ByteArray {
        val out = Buffer()
        out.write(int32BE(8 + boxes.sumOf { it.size }))
        out.write(latin1(atomId))
        boxes.forEach { out.write(it) }
        return out.readByteArray()
    }

    fun encodeIlst(tag: Mp4Tag): ByteArray {
        val body = Buffer()
        val covers = mutableListOf<ByteArray>()

        for (item in tag.items) {
            when (item) {
                is Mp4Item.Text ->
                    body.write(buildItem(item.atomId, buildDataBox(TYPE_TEXT, item.value.encodeToByteArray())))

                is Mp4Item.ReverseDns -> {
                    val mean = Buffer().also {
                        val payload = item.issuer.encodeToByteArray()
                        it.write(int32BE(12 + payload.size)); it.write(latin1("mean"))
                        it.write(int32BE(0)); it.write(payload)
                    }.readByteArray()
                    val name = Buffer().also {
                        val payload = item.identifier.encodeToByteArray()
                        it.write(int32BE(12 + payload.size)); it.write(latin1("name"))
                        it.write(int32BE(0)); it.write(payload)
                    }.readByteArray()
                    body.write(
                        buildItem("----", mean, name, buildDataBox(TYPE_TEXT, item.value.encodeToByteArray()))
                    )
                }

                is Mp4Item.NumberPair -> {
                    // trkn payload is 8 bytes, disk 6 (matching the original writer)
                    val payload = ByteArray(if (item.atomId == "trkn") 8 else 6)
                    payload[2] = (item.number ushr 8).toByte()
                    payload[3] = item.number.toByte()
                    payload[4] = (item.total ushr 8).toByte()
                    payload[5] = item.total.toByte()
                    body.write(buildItem(item.atomId, buildDataBox(TYPE_IMPLICIT, payload)))
                }

                is Mp4Item.Genre -> {
                    val payload = ByteArray(2)
                    payload[0] = (item.genreId ushr 8).toByte()
                    payload[1] = item.genreId.toByte()
                    body.write(buildItem("gnre", buildDataBox(TYPE_IMPLICIT, payload)))
                }

                is Mp4Item.Cover ->
                    covers += buildDataBox(if (item.isPng) TYPE_PNG else TYPE_JPEG, item.data)

                is Mp4Item.Binary ->
                    body.write(buildItem(item.atomId, item.rawData))
            }
        }
        if (covers.isNotEmpty()) {
            body.write(buildItem("covr", *covers.toTypedArray()))
        }

        val items = body.readByteArray()
        val out = Buffer()
        out.write(int32BE(8 + items.size))
        out.write(latin1("ilst"))
        out.write(items)
        return out.readByteArray()
    }

    // iTunes-style metadata handler box required inside a freshly created meta
    private fun buildHdlr(): Node.Leaf {
        val out = Buffer()
        out.write(int32BE(0)) // version + flags
        out.write(int32BE(0)) // predefined
        out.write(latin1("mdir"))
        out.write(latin1("appl"))
        out.write(ByteArray(10))
        return Node.Leaf("hdlr", out.readByteArray())
    }

    // ---- moov tree editing ----

    private fun findOrCreatePath(moovChildren: MutableList<Node>): Node.Container {
        // prefer moov>udta>meta, accept moov>meta; create moov>udta>meta when absent
        val udta = moovChildren.filterIsInstance<Node.Container>().firstOrNull { it.id == "udta" }
        val metaParent: MutableList<Node>
        val existingMeta = udta?.children?.filterIsInstance<Node.Container>()?.firstOrNull { it.id == "meta" }
            ?: moovChildren.filterIsInstance<Node.Container>().firstOrNull { it.id == "meta" }

        if (existingMeta != null) return existingMeta

        val meta = Node.Container("meta", ByteArray(4), mutableListOf(buildHdlr()))
        if (udta != null) {
            metaParent = udta.children
        } else {
            val newUdta = Node.Container("udta", ByteArray(0), mutableListOf())
            moovChildren += newUdta
            metaParent = newUdta.children
        }
        metaParent += meta
        return meta
    }

    fun write(io: FileIo, tag: Mp4Tag) {
        val moov = Mp4Atoms.findChild(io, 0, io.size, "moov")
            ?: throw CannotWriteException("This file does not appear to be an audio file")
        val moovStart = moov.dataStart - Mp4Atoms.HEADER_LENGTH
        val oldMoovEnd = moov.dataEnd

        io.position = moov.dataStart
        val moovData = io.readFully(moov.dataLength.toInt())
        val children = parseChildren(moovData, 0, moovData.size)
            ?: throw CannotWriteException("Unable to parse moov atom")

        // replace (or create) the ilst inside meta
        val meta = findOrCreatePath(children)
        val ilstIndex = meta.children.indexOfFirst { it.id == "ilst" }
        val ilstBytes = encodeIlst(tag)
        val newIlst = Node.Leaf("ilst", ilstBytes.copyOfRange(8, ilstBytes.size))
        if (ilstIndex >= 0) {
            meta.children[ilstIndex] = newIlst
        } else {
            meta.children += newIlst
        }

        val moovNode = Node.Container("moov", ByteArray(0), children)
        // moov is not in containerIds (never parsed as a child), so size logic is manual:
        val newMoovSize = 8 + children.sumOf { serializedSize(it) }
        val delta = newMoovSize - (oldMoovEnd - moovStart).toInt()

        var shift = 0
        if (delta != 0) {
            val freeSize = if (oldMoovEnd + 8 <= io.size) {
                io.position = oldMoovEnd
                val header = io.readFully(8)
                val size = header.readInt32BE(0).toUInt().toLong()
                val id = header.decodeLatin1(4, 8)
                if (id == "free" && size >= 8 && oldMoovEnd + size <= io.size) size else 0L
            } else 0L

            when {
                freeSize > 0 && freeSize - delta >= 8 -> {
                    // resize the free atom in place: bytes after it stay put
                    io.position = oldMoovEnd + delta
                    io.write(freeAtomHeader(freeSize - delta))
                }

                delta <= -8 -> {
                    io.position = oldMoovEnd + delta
                    io.write(freeAtomHeader((-delta).toLong()))
                }

                else -> {
                    // leave padding behind the new moov so the next edit does not shift again
                    shift = delta - freeSize.toInt() + FREE_PADDING
                    io.position = oldMoovEnd + freeSize
                    ShiftData.shiftDataByOffsetToMakeSpace(io, shift)
                    io.position = oldMoovEnd + delta
                    io.write(freeAtom(FREE_PADDING))
                }
            }
        }

        if (shift != 0) {
            patchChunkOffsets(moovNode, threshold = oldMoovEnd, delta = shift.toLong())
        }

        val out = Buffer()
        out.write(int32BE(newMoovSize))
        out.write(latin1("moov"))
        moovNode.children.forEach { serialize(it, out) }
        io.position = moovStart
        io.write(out.readByteArray())
        io.flush()
    }

    fun delete(io: FileIo) {
        write(io, Mp4Tag())
    }

    /** Adds [delta] to every stco/co64 offset pointing at or beyond [threshold]. */
    private fun patchChunkOffsets(node: Node, threshold: Long, delta: Long) {
        when (node) {
            is Node.Container -> node.children.forEach { patchChunkOffsets(it, threshold, delta) }
            is Node.Leaf -> when (node.id) {
                "stco" -> {
                    val data = node.data
                    if (data.size < 8) return
                    val count = data.readInt32BE(4)
                    for (i in 0 until count) {
                        val pos = 8 + i * 4
                        if (pos + 4 > data.size) break
                        val offset = data.readInt32BE(pos).toUInt().toLong()
                        if (offset >= threshold) {
                            int32BE((offset + delta).toInt()).copyInto(data, pos)
                        }
                    }
                }

                "co64" -> {
                    val data = node.data
                    if (data.size < 8) return
                    val count = data.readInt32BE(4)
                    for (i in 0 until count) {
                        val pos = 8 + i * 8
                        if (pos + 8 > data.size) break
                        val offset = (data.readInt32BE(pos).toUInt().toLong() shl 32) or
                            data.readInt32BE(pos + 4).toUInt().toLong()
                        if (offset >= threshold) {
                            val patched = offset + delta
                            int32BE((patched ushr 32).toInt()).copyInto(data, pos)
                            int32BE(patched.toInt()).copyInto(data, pos + 4)
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
