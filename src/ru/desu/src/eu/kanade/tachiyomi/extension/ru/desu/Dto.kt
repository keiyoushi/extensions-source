package eu.kanade.tachiyomi.extension.ru.desu

import kotlinx.serialization.Serializable

@Serializable
class SeriesWrapperDto<T>(
    val response: T,
)

@Serializable
class PageWrapperDto<T>(
    val pageNavParams: NavDto,
    val response: List<T>,
) {
    @Serializable
    class NavDto(
        val count: Int,
        val page: Int,
        val limit: Int,
    )
}

@Serializable
class MangaDetDto(
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
    class ImgDto(
        val original: String? = null,
    )
}

@Serializable
class MangaDetGenresDto(
    val genres: List<TagsDto>? = null,
) {
    @Serializable
    class TagsDto(
        val russian: String,
    )
}
