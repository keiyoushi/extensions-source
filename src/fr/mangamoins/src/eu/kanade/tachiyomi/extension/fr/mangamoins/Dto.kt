package eu.kanade.tachiyomi.extension.fr.mangamoins

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import java.util.Locale

internal fun String.toMangaSlug(): String = this.lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

internal fun String.unescapeHtml(): String = Parser.unescapeEntities(this, false)

@Serializable
class MangaListResponse(
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 10,
    val data: List<MangaListItem> = emptyList(),
)

@Serializable
class TrendResponse(
    val data: List<MangaListItem> = emptyList(),
)

@Serializable
class MangaListItem(
    val title: String,
    val cover: String = "",
    @SerialName("mangaSlug") val slug: String? = null,
    @SerialName("slug") val trendSlug: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaListItem.title.unescapeHtml()
        url = this@MangaListItem.slug ?: this@MangaListItem.trendSlug ?: title.toMangaSlug()
        thumbnail_url = this@MangaListItem.cover
    }
}

@Serializable
class MangaDetailsResponse(
    val info: MangaInfo,
    val chapters: List<ChapterItem> = emptyList(),
)

@Serializable
class MangaInfo(
    val title: String,
    val author: String = "",
    val status: String = "",
    val cover: String = "",
    val description: String = "",
)

@Serializable
class ChapterItem(
    val slug: String,
    val num: Float,
    val title: String = "",
    val time: Long = 0L,
)

@Serializable
class ScanResponse(
    val pageNumbers: Int,
    val pagesBaseUrl: String,
)
