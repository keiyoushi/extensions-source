package eu.kanade.tachiyomi.extension.vi.vitruyen

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
class ListingResponse(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
    val items: List<BrowseItem>,
    @SerialName("filter_options") val filterOptions: FilterOptions = FilterOptions(),
)

@Serializable
class SearchResponse(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
    val items: List<BrowseItem>,
)

@Serializable
class FilterOptions(
    val categories: List<Option> = emptyList(),
    val translators: List<Option> = emptyList(),
    val schedules: List<Option> = emptyList(),
)

@Serializable
class Option(
    val name: String,
    val slug: String,
)

@Serializable
class BrowseItem(
    private val slug: String,
    private val name: String,
    private val image: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = image
    }
}

@Serializable
class MangaDetails(
    private val slug: String,
    private val name: String,
    private val image: String? = null,
    private val descriptionHtml: String? = null,
    @SerialName("description") private val summary: String? = null,
    private val isCompleted: Boolean? = null,
    private val categories: List<Option> = emptyList(),
    val related: List<BrowseItem> = emptyList(),
    val chapters: List<ChapterItem> = emptyList(),
    val currentChapterContent: ChapterContent? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = image
        genre = categories.joinToString { it.name }
        description = (descriptionHtml ?: summary)?.parseHtmlText()
        status = when (isCompleted) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            null -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterItem(
    val name: String,
    val readUrl: String,
    val publishedAt: String? = null,
)

@Serializable
class ChapterContent(
    val content: List<String> = emptyList(),
)

private fun String.parseHtmlText(): String = Jsoup.parse(this, "", Parser.htmlParser()).wholeText()
    .replace(blankLinesRegex, "\n\n")
    .trim()

private val blankLinesRegex = Regex("""\n{3,}""")
