package eu.kanade.tachiyomi.multisrc.zmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.get
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class ZManga : KeiSource() {

    protected open val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/advanced-search/${pagePathSegment(page)}?order=popular").asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaSelector() = "div.flexbox2-item"

    open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.flexbox2-content a").attr("abs:href"))
        title = element.select("div.flexbox2-title > span").first()!!.text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    open fun popularMangaNextPageSelector() = "div.pagination .next"

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/advanced-search/${pagePathSegment(page)}?order=update").asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = document.select(latestUpdatesNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isProjectPage = filters.filterIsInstance<ProjectFilter>().any { it.toUriPart() == "project-filter-on" }

        val document = if (query.isBlank() && isProjectPage) {
            client.get("$baseUrl$projectPageString/page/$page".toHttpUrl()).asJsoup()
        } else {
            val url = "$baseUrl/advanced-search/${pagePathSegment(page)}".toHttpUrl().newBuilder()
            url.addQueryParameter("title", query)
            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> {
                        url.addQueryParameter("author", filter.state)
                    }
                    is YearFilter -> {
                        url.addQueryParameter("yearx", filter.state)
                    }
                    is StatusFilter -> {
                        val status = when (filter.state) {
                            Filter.TriState.STATE_INCLUDE -> "completed"
                            Filter.TriState.STATE_EXCLUDE -> "ongoing"
                            else -> ""
                        }
                        url.addQueryParameter("status", status)
                    }
                    is TypeFilter -> {
                        url.addQueryParameter("type", filter.toUriPart())
                    }
                    is OrderByFilter -> {
                        url.addQueryParameter("order", filter.toUriPart())
                    }
                    is GenreList -> {
                        filter.state
                            .filter { it.state }
                            .forEach { url.addQueryParameter("genre[]", it.id) }
                    }
                    else -> {}
                }
            }
            client.get(url.build()).asJsoup()
        }
        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.select(searchMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    open val projectPageString = "/project-list"

    open fun searchMangaSelector() = popularMangaSelector()

    open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val updatedManga = mangaDetailsParse(document)

        val updatedChapters = document.select(chapterListSelector()).map { element ->
            chapterFromElement(element)
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val thumb = document.select("div.series-thumb img")
        thumbnail_url = thumb.attr("data-lazy-src").takeIf { it.isNotBlank() }
            ?: thumb.attr("abs:src")
        author = document.select(".series-infolist li:contains(Author) span").text()
        artist = document.select(".series-infolist li:contains(Artist) span").text()
        status = parseStatus(document.select(".series-infoz .status").firstOrNull()?.ownText())
        description = document.select("div.series-synops").text()
        genre = document.select("div.series-genres a").joinToString { it.text() }

        // add series type(manga/manhwa/manhua/other) thinggy to genre
        document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
            if (it.isNotEmpty() && it != "-" && genre?.contains(it, true) != true) {
                genre = if (genre.isNullOrEmpty()) it else "$genre, $it"
            }
        }

        // add alternative name to manga description
        document.select(altNameSelector).firstOrNull()?.ownText()?.let {
            if (it.isNotEmpty()) {
                description = if (description.isNullOrEmpty()) {
                    altName + it
                } else {
                    description + "\n\n$altName" + it
                }
            }
        }
    }

    open val seriesTypeSelector = "div.block span.type"
    open val altNameSelector = ".series-title span"
    open val altName = "Alternative Name: "

    // ============================= Chapters ==============================

    // careful not to include download links
    open fun chapterListSelector() = "ul.series-chapterlist div.flexch-infoz a"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        name = element.select("span").first()!!.ownText()
        date_upload = parseDate(element.select("span.date").text())
    }

    protected open fun parseDate(dateString: String): Long = try {
        LocalDate.parse(dateString, dateFormatter).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
    } catch (_: Exception) {
        0L
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(baseUrl + chapter.url).asJsoup())

    open fun pageListParse(document: Document): List<Page> = document.select("div.reader-area img:not(noscript img)").mapIndexed { i, img ->
        val urlStr = img.attr("data-lazy-src").ifBlank { img.attr("src") }.replace("\\", "")
        Page(i, imageUrl = img.attr("src", urlStr).attr("abs:src"))
    }

    // ============================== Filters ==============================

    open val hasProjectPage = false

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/advanced-search/").asJsoup()
        return buildJsonArray {
            document.select("div.custom-checkbox input[name=\"genre[]\"]").forEach { element ->
                buildJsonObject {
                    put("id", element.attr("value"))
                    put("name", element.nextElementSibling()?.text() ?: element.attr("id"))
                }.let(::add)
            }
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.jsonArray?.map {
            Tag(
                it["id"]!!.string,
                it["name"]!!.string,
            )
        } ?: emptyList()

        val filters = mutableListOf<Filter<*>>(
            Filter.Header("You can combine filter."),
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            GenreList(genres),
        )
        if (hasProjectPage) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("NOTE: cant be used with other filter!"),
                    Filter.Header("$name Project List page"),
                    ProjectFilter(),
                ),
            )
        }
        return FilterList(filters)
    }

    // ============================= Utilities =============================

    protected fun pagePathSegment(page: Int): String = if (page > 1) "page/$page/" else ""

    private fun parseStatus(status: String?): Int {
        val lowerCaseStatus = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            lowerCaseStatus.contains("ongoing") -> SManga.ONGOING
            lowerCaseStatus.contains("completed") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
