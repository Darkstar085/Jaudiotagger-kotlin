package org.jaudiotagger.kt.audio.asf

import org.jaudiotagger.kt.CannotWriteException
import org.jaudiotagger.kt.tag.asf.AsfFieldContainers
import org.jaudiotagger.kt.tag.asf.AsfTag

/** Port of [org.jaudiotagger.audio.asf.util.TagConverter]. */
internal object AsfTagConverter {

    data class Distribution(
        val containers: Map<AsfContainerType, List<AsfMetadataDescriptor>>,
    ) {
        fun get(type: AsfContainerType): List<AsfMetadataDescriptor> =
            containers[type].orEmpty()
    }

    fun distributeMetadata(tag: AsfTag): Distribution {
        val buckets =
            AsfContainerType.distributionOrder.associateWith { mutableListOf<AsfMetadataDescriptor>() }
        for (descriptor in tag.internalDescriptors) {
            val highest = AsfFieldContainers.highestContainer(descriptor.name)
            var assigned = false
            for (container in AsfContainerType.distributionOrder) {
                if (!AsfContainerType.areInCorrectOrder(container, highest)) continue
                val bucket = buckets.getValue(container)
                if (isAddSupported(container, descriptor, bucket)) {
                    bucket += descriptor
                    assigned = true
                    break
                }
            }
            if (!assigned) {
                throw CannotWriteException(
                    "Cannot place ASF descriptor ${descriptor.name} (${descriptor.content.size} bytes)",
                )
            }
        }
        return Distribution(buckets)
    }

    private fun isAddSupported(
        container: AsfContainerType,
        descriptor: AsfMetadataDescriptor,
        existing: List<AsfMetadataDescriptor>,
    ): Boolean {
        if (container.checkConstraints(
                descriptor.name,
                descriptor.content,
                descriptor.valueType,
                descriptor.streamNumber,
                descriptor.languageIndex,
            ) != null
        ) {
            return false
        }
        if (!container.multiValued && existing.any { it.sameKey(descriptor) }) {
            return false
        }
        return true
    }
}
