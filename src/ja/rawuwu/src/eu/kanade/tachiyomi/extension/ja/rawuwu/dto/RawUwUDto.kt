package eu.kanade.tachiyomi.extension.ja.rawuwu.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawUwUResponseDto(
    @SerialName("manga_list") val mangaList: List<MangaListDto>,
    val pagi: PagiDto,
)

@Serializable
class MangaListDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_name") val mangaName: String,
    @SerialName("manga_cover_img") val mangaCoverImg: String,
)

@Serializable
class PagiDto(val button: ButtonDto)

@Serializable
class ButtonDto(val next: Int)

@Serializable
class MangaDetailResponseDto(
    val authors: List<AuthorDto>,
    val chapters: List<ChapterDto>,
    val detail: MangaDetailDto,
    val tags: List<TagDto>,
)

@Serializable
class MangaDetailDto(
    @SerialName("manga_id") val mangaId: Int,
    @SerialName("manga_name") val mangaName: String,
    @SerialName("manga_description") private val rawMangaDescription: String,
    @SerialName("manga_status") val mangaStatus: Boolean,
    @SerialName("manga_cover_img") val mangaCoverImg: String,
    @SerialName("manga_cover_img_full") val mangaCoverImgFull: String,
    @SerialName("manga_others_name") val mangaOthersName: String,
) {
    val mangaDescription = rawMangaDescription.takeIf(String::isNotEmpty)
}

@Serializable
class ChapterDto(
    @SerialName("chapter_title") private val rawChapterTitle: String,
    @SerialName("chapter_number") val chapterNumber: Float,
    @SerialName("chapter_date_published") val chapterDatePublished: String,
) {
    val chapterTitle = rawChapterTitle.takeIf(String::isNotEmpty)
}

@Serializable
class AuthorDto(@SerialName("author_name") val authorName: String)

@Serializable
class TagDto(@SerialName("tag_name") val tagName: String)

@Serializable
class ChapterPageResponseDto(@SerialName("chapter_detail") val chapterDetail: ChapterPageDto)

@Serializable
class ChapterPageDto(
    val server: String,
    @SerialName("chapter_content") val chapterContent: String,
)
