package org.jaudiotagger.kt.vorbiscomment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jaudiotagger.kt.tag.FieldKey
import org.jaudiotagger.kt.tag.vorbiscomment.VorbisCommentTag

class VorbisCommentNumberTotalTest {

    @Test
    fun combinedTrackReadsAsNumber() {
        val combined = tag("TRACKNUMBER" to "3/12")
        assertEquals("3", combined.first(FieldKey.TRACK))
        assertEquals(listOf("3"), combined.all(FieldKey.TRACK))
        assertEquals("3/12", combined.firstRaw("TRACKNUMBER"))
        assertEquals(null, combined.firstRaw("TRACKTOTAL"))

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        assertEquals("3", separate.first(FieldKey.TRACK))
        assertEquals(listOf("3"), separate.all(FieldKey.TRACK))
        assertEquals("3", separate.firstRaw("TRACKNUMBER"))
        assertEquals("12", separate.firstRaw("TRACKTOTAL"))
    }

    @Test
    fun combinedTrackReadsTotalFromNumber() {
        val combined = tag("TRACKNUMBER" to "3/12")
        assertEquals("12", combined.first(FieldKey.TRACK_TOTAL))
        assertEquals(listOf("12"), combined.all(FieldKey.TRACK_TOTAL))
        assertEquals(null, combined.firstRaw("TRACKTOTAL"))

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        assertEquals("12", separate.first(FieldKey.TRACK_TOTAL))
        assertEquals(listOf("12"), separate.all(FieldKey.TRACK_TOTAL))
        assertEquals("12", separate.firstRaw("TRACKTOTAL"))
    }

    @Test
    fun setTrackKeepsCombinedTotal() {
        val combined = tag("TITLE" to "Song", "TRACKNUMBER" to "3/12")
        combined.set(FieldKey.TRACK, "5")
        assertEquals("5", combined.first(FieldKey.TRACK))
        assertEquals("12", combined.first(FieldKey.TRACK_TOTAL))
        assertEquals("5/12", combined.firstRaw("TRACKNUMBER"))
        assertEquals(null, combined.firstRaw("TRACKTOTAL"))
        assertEquals(listOf("TITLE", "TRACKNUMBER"), combined.allFields.map { field -> field.id })

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        separate.set(FieldKey.TRACK, "5")
        assertEquals("5", separate.first(FieldKey.TRACK))
        assertEquals("5", separate.firstRaw("TRACKNUMBER"))
        assertEquals("12", separate.firstRaw("TRACKTOTAL"))
    }

    @Test
    fun setTrackWithSlashWritesRaw() {
        val combined = tag("TRACKNUMBER" to "3/12")
        combined.set(FieldKey.TRACK, "5/14")
        assertEquals("5", combined.first(FieldKey.TRACK))
        assertEquals("14", combined.first(FieldKey.TRACK_TOTAL))
        assertEquals("5/14", combined.firstRaw("TRACKNUMBER"))
        assertEquals(null, combined.firstRaw("TRACKTOTAL"))

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        separate.set(FieldKey.TRACK, "5/14")
        assertEquals("5/14", separate.firstRaw("TRACKNUMBER"))
        assertEquals("12", separate.firstRaw("TRACKTOTAL"))
        assertEquals("5", separate.first(FieldKey.TRACK))
        assertEquals("12", separate.first(FieldKey.TRACK_TOTAL))
    }

    @Test
    fun setTrackTotalRewritesCombinedNumber() {
        val combined = tag("TRACKNUMBER" to "3/12")
        combined.set(FieldKey.TRACK_TOTAL, "14")
        assertEquals("3", combined.first(FieldKey.TRACK))
        assertEquals("14", combined.first(FieldKey.TRACK_TOTAL))
        assertEquals("3/14", combined.firstRaw("TRACKNUMBER"))
        assertEquals(null, combined.firstRaw("TRACKTOTAL"))

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        separate.set(FieldKey.TRACK_TOTAL, "14")
        assertEquals("3", separate.firstRaw("TRACKNUMBER"))
        assertEquals("14", separate.firstRaw("TRACKTOTAL"))
    }

    @Test
    fun removeTrackMovesCombinedTotalToTrackTotal() {
        val combined = tag("TITLE" to "Song", "TRACKNUMBER" to "3/12", "ARTIST" to "A")
        combined.remove(FieldKey.TRACK)
        assertEquals(null, combined.first(FieldKey.TRACK))
        assertEquals("12", combined.first(FieldKey.TRACK_TOTAL))
        assertEquals(null, combined.firstRaw("TRACKNUMBER"))
        assertEquals("12", combined.firstRaw("TRACKTOTAL"))
        assertEquals(listOf("TITLE", "ARTIST", "TRACKTOTAL"), combined.allFields.map { field -> field.id })

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        separate.remove(FieldKey.TRACK)
        assertEquals(null, separate.firstRaw("TRACKNUMBER"))
        assertEquals("12", separate.firstRaw("TRACKTOTAL"))
        assertEquals("12", separate.first(FieldKey.TRACK_TOTAL))
    }

    @Test
    fun removeTrackTotalStripsCombinedSlash() {
        val combined = tag("TRACKNUMBER" to "3/12")
        combined.remove(FieldKey.TRACK_TOTAL)
        assertEquals("3", combined.first(FieldKey.TRACK))
        assertEquals(null, combined.first(FieldKey.TRACK_TOTAL))
        assertEquals("3", combined.firstRaw("TRACKNUMBER"))
        assertEquals(null, combined.firstRaw("TRACKTOTAL"))

        val separate = tag("TRACKNUMBER" to "3", "TRACKTOTAL" to "12")
        separate.remove(FieldKey.TRACK_TOTAL)
        assertEquals("3", separate.firstRaw("TRACKNUMBER"))
        assertEquals(null, separate.firstRaw("TRACKTOTAL"))
        assertEquals("3", separate.first(FieldKey.TRACK))
        assertEquals(null, separate.first(FieldKey.TRACK_TOTAL))
    }

    @Test
    fun spacesAndLeadingZerosAreKeptFromTheMatch() {
        val spaced = tag("TRACKNUMBER" to " 3 / 12 ")
        assertEquals("3", spaced.first(FieldKey.TRACK))
        assertEquals("12", spaced.first(FieldKey.TRACK_TOTAL))

        val padded = tag("TRACKNUMBER" to "03/12")
        assertEquals("03", padded.first(FieldKey.TRACK))
        assertEquals("12", padded.first(FieldKey.TRACK_TOTAL))
        assertEquals("03/12", padded.firstRaw("TRACKNUMBER"))
    }

    @Test
    fun nonMatchingValuesStayRaw() {
        for (raw in listOf("3/12 bonus", "3/", "A1")) {
            val tag = tag("TRACKNUMBER" to raw)
            assertEquals(raw, tag.first(FieldKey.TRACK), raw)
            assertEquals(null, tag.first(FieldKey.TRACK_TOTAL), raw)
            assertEquals(raw, tag.firstRaw("TRACKNUMBER"), raw)
            assertEquals(null, tag.firstRaw("TRACKTOTAL"), raw)
        }
    }

    @Test
    fun separateTotalOverridesCombinedSlash() {
        val tag = tag("TRACKNUMBER" to "3/12", "TRACKTOTAL" to "14")
        assertEquals("3", tag.first(FieldKey.TRACK))
        assertEquals("14", tag.first(FieldKey.TRACK_TOTAL))
        tag.remove(FieldKey.TRACK_TOTAL)
        assertEquals("3", tag.first(FieldKey.TRACK))
        assertEquals(null, tag.first(FieldKey.TRACK_TOTAL))
        assertEquals("3", tag.firstRaw("TRACKNUMBER"))
        assertEquals(null, tag.firstRaw("TRACKTOTAL"))
    }

    @Test
    fun setDiscKeepsCombinedTotal() {
        val tag = tag("DISCNUMBER" to "1/2")
        tag.set(FieldKey.DISC_NO, "2")
        assertEquals("2/2", tag.firstRaw("DISCNUMBER"))
        assertEquals("2", tag.first(FieldKey.DISC_NO))
        assertEquals("2", tag.first(FieldKey.DISC_TOTAL))
        assertEquals(null, tag.firstRaw("DISCTOTAL"))
    }

    @Test
    fun encoderAndAlbumArtistAreUnchanged() {
        val emptyVendor = VorbisCommentTag(vendor = "")
        assertEquals(null, emptyVendor.first(FieldKey.ENCODER))
        assertTrue(emptyVendor.all(FieldKey.ENCODER).isEmpty())

        val jriver = tag("ALBUM ARTIST" to "JRiver Artist")
        assertEquals("JRiver Artist", jriver.first(FieldKey.ALBUM_ARTIST))
        assertEquals(listOf("JRiver Artist"), jriver.all(FieldKey.ALBUM_ARTIST))
    }

    private fun tag(vararg fields: Pair<String, String>): VorbisCommentTag {
        val tag = VorbisCommentTag()
        for ((id, value) in fields) {
            tag.addRaw(id, value)
        }
        return tag
    }
}
