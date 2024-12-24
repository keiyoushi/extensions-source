package eu.kanade.tachiyomi.extension.zh.happymh.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Popular / Latest / Search pages
@Serializable
data class PopularResponseDto(val data: PopularData)

@Serializable
data class PopularData(val items: List<MangaDto>, val isEnd: Boolean)

@Serializable
data class MangaDto(
    val name: String,
    @SerialName("manga_code") val code: String,
    val cover: String,
) {
    val url = "/manga/$code"
}

// Chapters
@Serializable
class ChapterByPageResponseDataItem(
    val id: Long,
    val chapterName: String,
    val order: Int,
    val codes: String,
)

@Serializable
class ChapterByPageResponseData(
    val items: List<ChapterByPageResponseDataItem>,
    val isEnd: Int,
)

@Serializable
class ChapterByPageResponse(
    val data: ChapterByPageResponseData,
)

// Pages
@Serializable
data class PageListResponseDto(val data: PageListData)

@Serializable
data class PageListData(val scans: List<PageDto>) {
    @Serializable
    data class PageDto(val n: Int, val url: String)
}
