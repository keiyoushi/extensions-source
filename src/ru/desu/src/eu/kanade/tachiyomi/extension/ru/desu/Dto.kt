package eu.kanade.tachiyomi.extension.ru.desu

import kotlinx.serialization.Serializable

@Serializable
class InfoWrapperDto<T>(
    val manga: T,
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
    val manga_id: Long,
    val name: String,
    val russian: String,
    val kind: String?,
    val description: String?,
    val score: Float?,
    val score_votes: Long?,
    val age_limit: String?,
    val synonyms: List<String>,
    val cover: ImgDto,
    val translation_status: String?,
    val status: String?,
) {
    @Serializable
    class ImgDto(
        val preview: String?,
    )
}

@Serializable
class MangaDetGenresDto(
    val genres: List<TagsDto>?,
) {
    @Serializable
    class TagsDto(
        val name: String,
    )
}

@Serializable
class MangaDetAuthorsDto(
    val authors: List<PeopleDto>?,
) {
    @Serializable
    class PeopleDto(
        val name: String,
    )
}

@Serializable
class SeriesWrapperDto<T>(
    val chapters: T,
)

@Serializable
class ChaptersDto(
    val chapter_id: Long,
    val manga_id: Long,
    val volume: String,
    val number: String,
    val title: String?,
    val publish_date: Long,
    val status: String,
    val is_readable: Boolean,
    val view_url: String,
)

@Serializable
class ChapterWrapperDto<T>(
    val chapter: T,
)

@Serializable
class ChapterDataDto(
    val pages: List<ChapterPageDto>,
)

@Serializable
class ChapterPageDto(
    val url: String,
)
