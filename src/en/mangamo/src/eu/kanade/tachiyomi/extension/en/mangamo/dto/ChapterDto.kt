package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val alwaysFree: Boolean? = null,
    val id: Int? = null,
    val chapterNumber: Float? = null,
    val createdAt: Long? = null,
    val enabled: Boolean? = null,
    val name: String? = null,
    val onlyTransactional: Boolean? = null,
    val seriesId: Int? = null,
    val type: String? = null,
) {
    val isVolume get() = type == "volume"
}
