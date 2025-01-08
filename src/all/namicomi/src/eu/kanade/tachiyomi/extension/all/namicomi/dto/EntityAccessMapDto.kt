package eu.kanade.tachiyomi.extension.all.namicomi.dto

import eu.kanade.tachiyomi.extension.all.namicomi.NamicomiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias EntityAccessMapDto = ResponseDto<EntityAccessMapDataDto>

@Serializable
@SerialName(NamicomiConstants.entityAccessMap)
data class EntityAccessMapDataDto(
    override val attributes: EntityAccessMapAttributesDto? = null,
) : EntityDto()

@Serializable
data class EntityAccessMapAttributesDto(
    // Map of entity IDs to whether the user has access to them
    val map: Map<String, Boolean>,
) : AttributesDto()

@Serializable
data class EntityAccessRequestDto(
    val entities: List<EntityAccessRequestItemDto>,
)

@Serializable
data class EntityAccessRequestItemDto(
    val entityId: String,
    val entityType: String,
)
