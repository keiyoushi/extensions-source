package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    val id: Int? = null,
    val chapterNumber: Float? = null,
    val createdAt: Long? = null,
//    val description: String? = null,
    val enabled: Boolean? = null,
    val name: String? = null,
//    val type: String? = null,
    val onlyTransactional: Boolean? = null,
//    val pageCount: Int? = null,
//    val publishDate: Long? = null,
    val seriesId: Int? = null,
//    val updatedAt: Long? = null,
//    val uuid: String? = null,
)
