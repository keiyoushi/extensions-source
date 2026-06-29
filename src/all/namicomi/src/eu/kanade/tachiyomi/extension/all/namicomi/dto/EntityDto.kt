package eu.kanade.tachiyomi.extension.all.namicomi.dto

import kotlinx.serialization.Serializable

@Serializable
sealed class EntityDto {
    val id: String = ""
    val relationships: List<EntityDto> = emptyList()
    abstract val attributes: AttributesDto?
}

@Serializable
sealed interface AttributesDto

@Serializable
class UnknownEntity(override val attributes: AttributesDto? = null) : EntityDto()
