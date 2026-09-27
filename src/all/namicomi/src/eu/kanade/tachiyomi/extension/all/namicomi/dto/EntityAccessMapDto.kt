package eu.kanade.tachiyomi.extension.all.namicomi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias EntityAccessMapDto = ResponseDto<EntityAccessMapDataDto>

@Serializable
@SerialName("entity_access_map")
class EntityAccessMapDataDto(
    override val attributes: EntityAccessMapAttributesDto? = null,
) : EntityDto()

@Serializable
class EntityAccessMapAttributesDto(
    val map: Map<String, Boolean>,
) : AttributesDto

@Serializable
class EntityAccessRequestDto(
    val entities: List<EntityAccessRequestItemDto>,
)

@Serializable
class EntityAccessRequestItemDto(
    val entityId: String,
    val entityType: String,
)
