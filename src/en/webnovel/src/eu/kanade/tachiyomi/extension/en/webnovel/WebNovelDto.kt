package eu.kanade.tachiyomi.extension.en.webnovel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ResponseDto<T>(
    val code: Int,
    val data: T?,
    val msg: String,
)

@Serializable
data class QuerySearchResponseDto(
    @SerialName("comicInfo") val browseResponse: BrowseResponseDto,
)

@Serializable
data class BrowseResponseDto(
    val isLast: Int,
    @JsonNames("comicItems") val items: List<ComicInfoDto>,
)

@Serializable
data class ComicInfoDto(
    @JsonNames("bookId", "comicId") val id: String,
    @JsonNames("bookName", "comicName") val name: String,
)

@Serializable
data class ComicDetailInfoResponseDto(
    @SerialName("comicInfo") val info: ComicDetailInfoDto,
)

@Serializable
data class ComicDetailInfoDto(
    @SerialName("comicId") val id: String,
    @SerialName("comicName") val name: String,
    val actionStatus: Int,
    val authorName: String,
    val categoryName: String,
    val description: String,
    val updateCycle: String,
) {
    companion object {
        const val ONGOING = 1
        const val COMPLETED = 2
        const val ON_HIATUS = 3
    }
}

@Serializable
data class ComicChapterListDto(
    val comicInfo: ComicInfoDto,
    val comicChapters: List<ComicChapterDto>,
)

@Serializable
data class ComicChapterDto(
    @SerialName("chapterId") val id: String,
    @SerialName("chapterName") val name: String,
    val publishTime: String,
    val price: Int,
    val isVip: Int,
    val isAuth: Int,
    val chapterLevel: Int,
    val userLevel: Int,
)

@Serializable
data class ChapterContentResponseDto(
    @SerialName("chapterInfo") val chapterContent: ChapterContentDto,
)

@Serializable
data class ChapterContentDto(
    @SerialName("chapterId") val id: Long,
    @SerialName("chapterPage") val pages: List<ChapterPageDto>,
)

@Serializable
data class ChapterPageDto(
    @SerialName("pageId") val id: String,
    val url: String,
)
