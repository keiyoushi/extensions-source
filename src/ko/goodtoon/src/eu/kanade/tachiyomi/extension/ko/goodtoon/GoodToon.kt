package eu.kanade.tachiyomi.extension.ko.goodtoon

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GoodToon : MadaraNoAjax() {

    // Card grid has no post IDs; series are addressed by their /manga/<slug>/ path.
    override val supportsPostId = false

    // admin-ajax.php is blocked (403); chapters come from /manga/<slug>/ajax/chapters/.
    override val chapterMode = ChapterMode.MangaAjax

    override val sendViewCount = false

    override val searchQueryParameter = "q"

    override val mangaDetailsSelectorAuthor = "div.manga-summary-author .author-text, div.manga-summary-author"
    override val mangaDetailsSelectorStatus = "div.summary-meta-row .meta-value"
    override val mangaDetailsSelectorDescription = "div.manga-summary-desc"
    override val mangaDetailsSelectorThumbnail = "div.manga-summary-cover img"

    override val completedStatus = arrayOf("completed", "완결")
    override val ongoingStatus = arrayOf("ongoing", "on going", "updating", "연재중")

    private val chapterNumberRegex = Regex("""(\d+(?:\.\d+)?)화""")

    override fun nextPageSelector() = "div.pagination a.page-numbers:containsOwn(다음)"

    override fun archiveUrlBuilder(page: Int, order: String, path: String, query: String) = baseUrl.toHttpUrl().resolve(path)!!.newBuilder().apply {
        if (page > 1) addQueryParameter("pg", page.toString())
        if (order.isNotEmpty()) addQueryParameter(orderQueryParameter, order)
        if (query.isNotEmpty()) addQueryParameter(searchQueryParameter, query)
    }

    override suspend fun getPopularManga(page: Int) = archivePage(page, "", "/recommend/")
    override suspend fun getLatestUpdates(page: Int) = archivePage(page, "", "/")

    // The site ignores ?q= on genre pages, so the query always wins over the genre filter.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.route()
        return when {
            query.isNotBlank() -> archivePage(page, "", "/", query)
            genre != null -> archivePage(page, "", genre.second)
            else -> archivePage(page, "", "/")
        }
    }

    override fun parseArchive(document: Document): List<SManga> = document.select("a.card[href*='/manga/']").mapNotNull { element ->
        val href = element.attrOrNull("abs:href") ?: return@mapNotNull null
        val mangaPath = href.toHttpUrl().encodedPath
        SManga.create().apply {
            url = mangaPath
            title = element.selectFirst("div.subject")?.textOrNull() ?: return@mapNotNull null
            // Genre pages use a different card template whose cover has no img-responsive class;
            // in both templates the cover is the only direct img child that is not the platform icon.
            thumbnail_url = element.selectFirst("div.thumb > img:not(.platform-icon)")?.let { processThumbnail(imageFromElement(it), true) }
            memo = mangaMemo(mangaPath, emptyList())
        }
    }

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        status = document.selectFirst(mangaDetailsSelectorStatus)?.text()?.toStatus() ?: SManga.UNKNOWN
        genre = document.selectFirst("div.manga-summary-genres")?.textOrNull()
    }

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? = super.chapterFromElement(element, mangaPath)?.apply {
        val link = element.selectFirst(chapterUrlSelector) ?: return null
        link.ownTextOrNull()?.let { name = it }
        chapterNumberRegex.findAll(name).lastOrNull()?.groupValues?.get(1)?.toFloatOrNull()?.let { chapter_number = it }
    }

    // Images sit directly in div.reading-content without per-image wrappers.
    override fun parsePages(document: Document): List<Page> = document.select("div.reading-content img").mapIndexedNotNull { index, element ->
        imageFromElement(element)?.let { Page(index, document.location(), it) }
    }

    override val chapterDateFormat = DateTimeFormatter.ofPattern("yy.MM.dd", Locale.KOREA)

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    override suspend fun fetchFilterData(): JsonElement = buildJsonArray {
        client.get(baseUrl).asJsoup().select("button.genre[data-genre]").forEach { element ->
            val slug = element.attrOrNull("data-genre") ?: return@forEach
            val name = element.textOrNull() ?: return@forEach
            add(
                buildJsonObject {
                    put("name", name)
                    put("slug", slug)
                    put("path", "/$genreDirectory/$slug/")
                },
            )
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj.getStringOrNull("name") ?: return@mapNotNull null
            val path = obj.getStringOrNull("path") ?: return@mapNotNull null
            name to path
        }
        return FilterList(
            buildList {
                if (genres.isNotEmpty()) {
                    add(GenreFilter(intl["genre_filter_title"], intl["adult_content_filter_all"], genres))
                }
            },
        )
    }
}

private class GenreFilter(name: String, allLabel: String, private val genres: List<Pair<String, String>>) : Filter.Select<String>(name, arrayOf(allLabel) + genres.map { it.first }.toTypedArray()) {
    fun route() = genres.getOrNull(state - 1)
}
