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
data class ChapterListDto(val chapterList: List<ChapterDto>) {
    @Serializable
    data class ChapterDto(val id: String, val chapterName: String)
}

// Pages
@Serializable
data class PageListResponseDto(val data: PageListData)

@Serializable
data class PageListData(val scans: List<PageDto>) {
    @Serializable
    data class PageDto(val n: Int, val url: String)
}
