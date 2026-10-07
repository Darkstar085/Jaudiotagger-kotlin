package org.jaudiotagger.kt.tag.id3

import org.jaudiotagger.kt.tag.FieldKey

/**
 * Parsed N/M track or disc number from ID3 TRCK/TPOS (and v2.2 TRK/TPA) frames.
 * Ported from [org.jaudiotagger.tag.datatype.PartOfSet.PartOfSetValue].
 */
class PartOfSetValue(rawText: String = "") {
    private var count: Int? = null
    private var total: Int? = null
    private var extra: String? = null
    private var rawCount: String? = null
    private var rawTotal: String? = null
    private var rawTextValue: String = rawText

    init {
        if (rawText.isNotEmpty()) initFromValue(rawText)
    }

    constructor(count: Int?, total: Int?) : this("") {
        this.count = count
        this.total = total
        rawCount = count?.toString()
        rawTotal = total?.toString()
        resetValueFromCounts()
    }

    private fun initFromValue(value: String) {
        rawTextValue = value
        val withTotal = TRACK_WITH_TOTAL.matchEntire(value)
        if (withTotal != null) {
            extra = withTotal.groupValues[3]
            count = withTotal.groupValues[1].toIntOrNull() ?: 0
            rawCount = withTotal.groupValues[1]
            total = withTotal.groupValues[2].toIntOrNull()
            rawTotal = withTotal.groupValues[2]
            return
        }
        val numberOnly = TRACK_NUMBER_ONLY.matchEntire(value)
        if (numberOnly != null) {
            extra = numberOnly.groupValues[2]
            count = numberOnly.groupValues[1].toIntOrNull() ?: 0
            rawCount = numberOnly.groupValues[1]
        }
    }

    companion object {
        private val TRACK_WITH_TOTAL = Regex("([0-9]+)/([0-9]+)(.*)", RegexOption.IGNORE_CASE)
        private val TRACK_NUMBER_ONLY = Regex("([0-9]+)(.*)", RegexOption.IGNORE_CASE)
    }

    private fun resetValueFromCounts() {
        val sb = StringBuilder()
        sb.append(rawCount ?: "0")
        if (rawTotal != null) sb.append('/').append(rawTotal)
        if (extra != null) sb.append(extra)
        rawTextValue = sb.toString()
    }

    fun getCount(): Int? = count
    fun getTotal(): Int? = total

    fun getCountAsText(): String? = rawCount
    fun getTotalAsText(): String? = rawTotal

    fun setCount(count: Int) {
        this.count = count
        rawCount = count.toString()
        resetValueFromCounts()
    }

    fun setCount(count: String) {
        count.toIntOrNull()?.let {
            this.count = it
            rawCount = count
            resetValueFromCounts()
        }
    }

    fun setTotal(total: Int) {
        this.total = total
        rawTotal = total.toString()
        resetValueFromCounts()
    }

    fun setTotal(total: String) {
        total.toIntOrNull()?.let {
            this.total = it
            rawTotal = total
            resetValueFromCounts()
        }
    }

    fun toRawText(): String = rawTextValue

    override fun toString(): String = rawTextValue
}

internal fun isNumberTotalFrameId(id: String): Boolean =
    id == "TRCK" || id == "TPOS" || id == "TRK" || id == "TPA"

internal fun isNumberFieldKey(key: FieldKey): Boolean =
    key == FieldKey.TRACK || key == FieldKey.DISC_NO || key == FieldKey.MOVEMENT_NO

internal fun isTotalFieldKey(key: FieldKey): Boolean =
    key == FieldKey.TRACK_TOTAL || key == FieldKey.DISC_TOTAL || key == FieldKey.MOVEMENT_TOTAL
