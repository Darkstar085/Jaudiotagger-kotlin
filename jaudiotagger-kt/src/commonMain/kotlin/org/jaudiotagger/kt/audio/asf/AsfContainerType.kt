package org.jaudiotagger.kt.audio.asf

/**
 * ASF metadata container capabilities. Ported from
 * [org.jaudiotagger.audio.asf.data.ContainerType].
 */
private fun asfGuid(vararg bytes: Int): ByteArray = ByteArray(16) { bytes[it].toByte() }

internal enum class AsfContainerType(
    val guid: ByteArray,
    private val maxDataLenBits: Int,
    val guidEnabled: Boolean,
    val streamEnabled: Boolean,
    val languageEnabled: Boolean,
    val multiValued: Boolean,
) {
    CONTENT_BRANDING(
        asfGuid(0xFA, 0xB3, 0x11, 0x22, 0x23, 0xBD, 0xD2, 0x11, 0xB4, 0xB7, 0x00, 0xA0, 0xC9, 0x55, 0xFC, 0x6E),
        32, false, false, false, false,
    ),
    CONTENT_DESCRIPTION(
        asfGuid(0x33, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11, 0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C),
        16, false, false, false, false,
    ),
    EXTENDED_CONTENT(
        asfGuid(0x40, 0xA4, 0xD0, 0xD2, 0x07, 0xE3, 0xD2, 0x11, 0x97, 0xF0, 0x00, 0xA0, 0xC9, 0x5E, 0xA8, 0x50),
        16, false, false, false, false,
    ),
    METADATA_OBJECT(
        asfGuid(0xEA, 0xCB, 0xF8, 0xC5, 0xAF, 0x5B, 0x77, 0x48, 0x84, 0x67, 0xAA, 0x8C, 0x44, 0xFA, 0x4C, 0xCA),
        16, false, true, false, true,
    ),
    METADATA_LIBRARY(
        asfGuid(0x94, 0x1C, 0x23, 0x44, 0x98, 0x94, 0xD1, 0x49, 0xA1, 0x41, 0x1D, 0x13, 0x4E, 0x45, 0x70, 0x54),
        32, true, true, true, true,
    ),
    ;

    val maxDataLength: Long = (1L shl maxDataLenBits) - 1

    fun isWithinValueRange(length: Long): Boolean = length in 0..maxDataLength

    fun checkConstraints(
        name: String,
        data: ByteArray,
        type: Int,
        stream: Int,
        language: Int,
    ): String? {
        if (stream !in 0..127 || (!streamEnabled && stream != 0)) {
            return "invalid stream $stream for $name"
        }
        if (type == AsfMetadataDescriptor.TYPE_GUID && !guidEnabled) {
            return "GUID type not allowed in $name"
        }
        if ((language != 0 && !languageEnabled) || language !in 0 until AsfMetadataDescriptor.MAX_LANG_INDEX) {
            return "invalid language $language for $name"
        }
        if (!isWithinValueRange(data.size.toLong())) {
            return "data too large for $name (${data.size} bytes)"
        }
        if (this == CONTENT_DESCRIPTION) {
            if (type != AsfMetadataDescriptor.TYPE_STRING) {
                return "content description allows strings only"
            }
            if (name.uppercase() !in CONTENT_DESCRIPTION_NAMES) {
                return "name not allowed in content description"
            }
        }
        if (this == CONTENT_BRANDING && name.uppercase() !in CONTENT_BRANDING_NAMES) {
            return "name not allowed in content branding"
        }
        return null
    }

    companion object {
        private val CONTENT_DESCRIPTION_NAMES = setOf("AUTHOR", "TITLE", "RATING", "COPYRIGHT", "DESCRIPTION")
        private val CONTENT_BRANDING_NAMES = setOf(
            "BANNER_IMAGE", "BANNER_IMAGE_TYPE", "BANNER_IMAGE_URL", "COPYRIGHT_URL",
        )

        /** Capability order used by [AsfTagConverter.distributeMetadata]. */
        val distributionOrder: List<AsfContainerType> = listOf(
            CONTENT_DESCRIPTION,
            CONTENT_BRANDING,
            EXTENDED_CONTENT,
            METADATA_OBJECT,
            METADATA_LIBRARY,
        )

        /** Read order from [org.jaudiotagger.audio.asf.util.TagConverter.createTagOf]. */
        val readOrder: List<AsfContainerType> = entries

        fun fromGuid(guid: ByteArray): AsfContainerType? =
            entries.firstOrNull { it.guid.contentEquals(guid) }

        fun areInCorrectOrder(low: AsfContainerType, high: AsfContainerType): Boolean =
            distributionOrder.indexOf(low) <= distributionOrder.indexOf(high)
    }
}
