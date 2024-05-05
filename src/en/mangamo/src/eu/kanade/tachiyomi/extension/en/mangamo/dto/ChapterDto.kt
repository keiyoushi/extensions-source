package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    val id: Int?,
    val chapterNumber: Float?,
    val createdAt: Long?,
    val description: String?,
    val enabled: Boolean?,
    val name: String?,
    val type: String?,
    val onlyTransactional: Boolean?,
    val pageCount: Int?,
    val publishDate: Long?,
    val seriesId: Int?,
    val updatedAt: Long?,
    val uuid: String?,
)
