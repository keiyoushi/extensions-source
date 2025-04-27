package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import kotlinx.serialization.Serializable

@Serializable
sealed class EntityDto {
    val id: Long = -1
}

@Serializable
class UnknownEntity() : EntityDto()
