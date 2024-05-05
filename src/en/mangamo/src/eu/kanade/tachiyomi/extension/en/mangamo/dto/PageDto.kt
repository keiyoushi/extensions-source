package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    val id: Int,
    val chapterId: Int,
    val pageNumber: Int,
    val thumb: String,
    val height: Int,
    val width: Int,
    val uri: String,
)
