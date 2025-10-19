package eu.kanade.tachiyomi.extension.en.weebdex.dto

import WeebDexHelper
import eu.kanade.tachiyomi.extension.en.weebdex.WeebDexConstants
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    private val data: List<ChapterDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page * limit < total
    fun toSChapterList(): List<SChapter> {
        return data.map { it.toSChapter() }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String? = null,
    private val chapter: String? = null,
    @SerialName("published_at") private val publishedAt: String = "",
    private val data: List<PageData>? = null,
    @SerialName("data_optimized") private val dataOptimized: List<PageData>? = null,
    private val relationships: ChapterRelationshipsDto? = null,
)
{
    @Contextual
    private val helper = WeebDexHelper()

    fun toSChapter(): SChapter {
       return SChapter.create().apply {
            url = "/chapter/${id}"
            val chapTitle = title
            name = if (chapTitle.isNullOrBlank()) "Chapter $chapter" else chapTitle
            date_upload = helper.parseDate(publishedAt)
            scanlator = relationships?.groups?.joinToString(", ") { it.name }
        }
    }
    fun toPageList(): List<Page> {
        val pagesArray = dataOptimized ?: data ?: emptyList()
        val pages = mutableListOf<Page>()

        pagesArray.forEachIndexed { index, pageData ->
            // pages in spec have 'name' field and images served from /data/{id}/{filename}
            val filename = pageData.name
            val chapterId = id
            val imageUrl = filename?.takeIf { it.isNotBlank() && chapterId.isNotBlank() }
                ?.let { "${WeebDexConstants.CDN_DATA_URL}/$chapterId/$it" }
            pages.add(Page(index, imageUrl = imageUrl))
        }
        return pages
    }
}

@Serializable
class ChapterRelationshipsDto(
    val groups: List<NamedEntity> = emptyList(),
)

@Serializable
class PageData(
    val name: String? = null,
)
