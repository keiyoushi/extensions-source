package eu.kanade.tachiyomi.extension.ru.desu.dto

import kotlinx.serialization.Serializable
@Serializable
data class SeriesWrapperDto<T>(
    val response: T,
)

@Serializable
data class PageWrapperDto<T>(
    val pageNavParams: NavDto,
    val response: List<T>,
) {
    @Serializable
    data class NavDto(
        val count: Int,
        val page: Int,
        val limit: Int,
    )
}

@Serializable
data class MangaDetDto(
    val id: Long,
    val name: String,
    val russian: String,
    val kind: String? = null,
    val description: String? = null,
    val score: Float? = null,
    val score_users: Long? = null,
    val age_limit: String? = null,
    val synonyms: String? = null,
    val image: ImgDto,
    val trans_status: String? = null,
    val status: String? = null,
) {
    @Serializable
    data class ImgDto(
        val original: String? = null,
    )
}

@Serializable
data class MangaDetGenresDto(
    val genres: List<TagsDto>? = null,
) {
    @Serializable
    data class TagsDto(
        val russian: String,
    )
}
