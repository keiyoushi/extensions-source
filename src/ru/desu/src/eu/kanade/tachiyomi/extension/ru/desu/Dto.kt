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
    val kind: String?,
    val description: String?,
    val score: Float?,
    val score_users: Long?,
    val age_limit: String?,
    val synonyms: String?,
    val image: ImgDto,
    val trans_status: String?,
    val status: String?,
) {
    @Serializable
    class ImgDto(
        val original: String?,
    )
}

@Serializable
class MangaDetGenresDto(
    val genres: List<TagsDto>?,
) {
    @Serializable
    class TagsDto(
        val russian: String,
    )
}

@Serializable
class MangaDetAuthorsDto(
    val authors: List<PeopleDto>?,
) {
    @Serializable
    class PeopleDto(
        val people_name: String,
    )
}
