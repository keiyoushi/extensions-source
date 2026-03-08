package eu.kanade.tachiyomi.extension.ja.rawuwu.dto

import kotlinx.serialization.Serializable

@Serializable
data class RawUwUResponseDto(
    val manga_list: List<MangaListDto>? = null,
    val pagi: PagiDto? = null,
)

@Serializable
data class MangaListDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_name") val mangaName: String,
    @SerialName("manga_cover_img") val mangaCoverImg: String,
)

@Serializable
data class PagiDto(val button: ButtonDto? = null)

@Serializable
data class ButtonDto(val next: Int? = null)

@Serializable
data class MangaDetailResponseDto(
    val authors: List<AuthorDto>? = null,
    val chapters: List<ChapterDto>? = null,
    val detail: MangaDetailDto? = null,
    val tags: List<TagDto>? = null,
)

@Serializable
data class MangaDetailDto(
    val manga_id: Int,
    val manga_name: String,
    val manga_description: String? = null,
    val manga_status: Boolean? = null,
    val manga_cover_img: String? = null,
    val manga_cover_img_full: String? = null,
    val manga_others_name: String? = null,
)

@Serializable
data class ChapterDto(
    val chapter_title: String? = null,
    val chapter_number: Float? = null,
    val chapter_date_published: String? = null,
    val server: String? = null,
    val chapter_content: String? = null,
)

@Serializable
data class AuthorDto(val author_name: String)

@Serializable
data class TagDto(val tag_name: String)

@Serializable
data class ChapterPageResponseDto(val chapter_detail: ChapterDto? = null)
