package eu.kanade.tachiyomi.extension.all.mangadraft.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangadraftPageDTO(
    val id: Long,
    val number: Int,
    val url: String,
    val viewed: Boolean,
    val liked: Boolean,
    val likes: Int,
    val comments: Int,
    val views: Int,
    val cat: Long,
    val ago: String,
    val delayed: Boolean,
    val type: String,
    val paying: Int,
    val w: Int,
    val h: Int,
)

// Top-level DTO: map of category ID -> list of pages
typealias PagesByCategory = Map<String, List<MangadraftPageDTO>>
