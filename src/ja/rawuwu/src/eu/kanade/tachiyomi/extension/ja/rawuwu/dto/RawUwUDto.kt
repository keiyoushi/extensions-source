package eu.kanade.tachiyomi.extension.ja.rawuwu.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawUwUResponseDto(
    @SerialName("manga_list") val mangaList: List<MangaListDto>? = null,
    val pagi: PagiDto? = null,
)

@Serializable
class MangaListDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_name") val mangaName: String,
    @SerialName("manga_cover_img") val mangaCoverImg: String,
)

@Serializable
class PagiDto(val button: ButtonDto? = null)

@Serializable
class ButtonDto(val next: Int? = null)

@Serializable
class MangaDetailResponseDto(
    val authors: List<AuthorDto>? = null,
    val chapters: List<ChapterDto>? = null,
    val detail: MangaDetailDto? = null,
    val tags: List<TagDto>? = null,
)

@Serializable
class MangaDetailDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_name") val mangaName: String,
    @SerialName("manga_description") val mangaDescription: String? = null,
    @SerialName("manga_status") val mangaStatus: Boolean? = null,
    @SerialName("manga_cover_img") val mangaCoverImg: String? = null,
    @SerialName("manga_cover_img_full") val mangaCoverImgFull: String? = null,
    @SerialName("manga_others_name") val mangaOthersName: String? = null,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_title") val chapterTitle: String? = null,
    @SerialName("chapter_number") val chapterNumber: Float? = null,
    @SerialName("chapter_date_published") val chapterDatePublished: String? = null,
    val server: String? = null,
    @SerialName("chapter_content") val chapterContent: String? = null,
)

@Serializable
class AuthorDto(@SerialName("author_name") val authorName: String)

@Serializable
class TagDto(@SerialName("tag_name") val tagName: String)

@Serializable
class ChapterPageResponseDto(@SerialName("chapter_detail") val chapterDetail: ChapterDto? = null)
