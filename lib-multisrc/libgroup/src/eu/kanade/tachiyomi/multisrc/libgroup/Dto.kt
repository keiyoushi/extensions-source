package eu.kanade.tachiyomi.multisrc.libgroup

import kotlinx.serialization.Serializable

// Library Container
@Serializable
class PageWrapperDto<T>(
    val data: List<T>,
)

// Manga Details
@Serializable
class SeriesWrapperDto<T>(
    val data: T,

)

@Serializable
class MangaDetDto(
    val slug: String,
    val name: String,
    val rus_name: String?,
    val eng_name: String?,
    val cover: ImgDto,
    val summary: String?,
    val type: TypeDto?,
    val genres: List<TagsDto>?,
    val tags: List<TagsDto>?,
    val authors: List<AuthorsDto>?,
    val artists: List<AuthorsDto>?,
    val status: TypeDto?,
    val scanlateStatus: TypeDto?,
) {
    @Serializable
    class ImgDto(
        val default: String?,
    )

    @Serializable
    class TypeDto(
        val label: String,
    )

    @Serializable
    class TagsDto(
        val name: String,
    )

    @Serializable
    class AuthorsDto(
        val name: String,
    )
}

@Serializable
class ChapterDto(
    val pages: List<PagesDto>,
) {
    @Serializable
    class PagesDto(
        val url: String,
    )
}

@Serializable
class BookDto(
    val volume: String,
    val number: String,
    val name: String?,
    val branches: List<BranchDto>,
) {
    @Serializable
    class BranchDto(
        val branch_id: Long?,
        val created_at: String?,
        val teams: List<TeamDto?>?,
    ) {
        @Serializable
        class TeamDto(
            val name: String?,
        )
    }
}
