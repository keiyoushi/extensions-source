package eu.kanade.tachiyomi.extension.ru.desu

import kotlinx.serialization.Serializable
@Serializable
class InfoWrapperDto<T>(
    val manga: T,
)

@Serializable
class PageWrapperDto<T>(
    val pagination: NavDto,
    val mangas: List<T>,
) {
    @Serializable
    class NavDto(
        val current_page: Int,
        val last_page: Int,
    )
}

@Serializable
class MangaDetDto(
    val id: Long,
    val name: String,
    val russian: String,
    val kind: String?,
    val description: String?,
    val score: ScoreDto?,
    val content_rating: String?,
    val synonyms: List<String>?,
    val cover: ImgDto,
    val trans_status: String?,
    val status: String?,

    val genres: List<GenreDto>? = null,
    val authors: List<AuthorDto>? = null,
) {
    @Serializable
    class ScoreDto(
        val value: Float?,
        val votes: Long?,
    )

    @Serializable
    class ImgDto(
        val preview: String?,
    )

    @Serializable
    class GenreDto(
        val name: String,
    )

    @Serializable
    class AuthorDto(
        val name: String,
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
    val id: Long,
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
