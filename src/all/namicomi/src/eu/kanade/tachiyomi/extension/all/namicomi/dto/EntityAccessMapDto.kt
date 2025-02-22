package eu.kanade.tachiyomi.extension.all.namicomi.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.extension.all.namicomi.NamiComiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias EntityAccessMapDto = ResponseDto<EntityAccessMapDataDto>

@Serializable
@SerialName(NamiComiConstants.entityAccessMap)
class EntityAccessMapDataDto(
    override val attributes: EntityAccessMapAttributesDto? = null,
) : EntityDto()

@Serializable
class EntityAccessMapAttributesDto(
    // Map of entity IDs to whether the user has access to them
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
