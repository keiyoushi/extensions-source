package eu.kanade.tachiyomi.extension.zh.happymh

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Popular / Latest / Search pages
@Serializable
class PopularResponseDto(val data: PopularData) {
    fun toMangasPage(): MangasPage {
        val items = data.items.map {
            SManga.create().apply {
                title = it.name
                url = it.url
                thumbnail_url = it.cover
            }
        }
        return MangasPage(items, data.isEnd.not())
    }
}

@Serializable
class PopularData(val items: List<MangaDto>, val isEnd: Boolean)

@Serializable
class MangaDto(
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
)

@Serializable
class ChapterByPageResponseData(
    val items: List<ChapterByPageResponseDataItem>,
    val total: Int,
    val curr: Int,
)

@Serializable
class ChapterByPageResponse(
    val data: ChapterByPageResponseData,
)

// Pages
@Serializable
class PageListResponseDto(val data: PageListData)

@Serializable
class PageListData(
    val scans: String,
    val isEncode: Boolean,
)

@Serializable
data class PageDto(val n: Int, val url: String)
