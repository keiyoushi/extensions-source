package eu.kanade.tachiyomi.extension.all.mangadraft.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaDraftPageDTO(
    val id: Long,
    val number: Int,
    val url: String,
)

// Top-level DTO: map of category ID -> list of pages
typealias PagesByCategory = Map<String, List<MangaDraftPageDTO>>
