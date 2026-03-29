package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class PhiliaScans :
    Madara(
        "Philia Scans",
        "https://philiascans.org",
        "en",
    ) {
    override val versionId: Int = 4

    override val useNewChapterEndpoint = false

    private var searchNonce: String = ""

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/all-mangas/?paged=$page", headers)

    override fun popularMangaSelector() = ".original .unit"
    override val popularMangaUrlSelector = ".info a.c-title"
    override val popularMangaUrlSelectorImg = ".poster img:not(.flag-icon)"
    override fun popularMangaNextPageSelector() = ".pagination a.page-link[rel=next]"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/recently-updated/?page=$page", headers)

    private fun getSearchNonce(): String {
        if (searchNonce.isNotEmpty()) return searchNonce
        try {
            val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
            val script = document.select("script:containsData(liveSearchData)").firstOrNull()?.data()
            if (script != null) {
                searchNonce = script.substringAfter("\"nonce\":\"").substringBefore("\"")
            }
        } catch (e: Exception) {
            // Ignore and fallback to trying without nonce
        }
        return searchNonce
    }

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val nonce = getSearchNonce()
            val form = FormBody.Builder()
                .add("action", "live_search")
                .add("search_query", query)
                .apply {
                    if (nonce.isNotEmpty()) {
                        add("security", nonce)
                    }
                }
                .build()

            return POST("$baseUrl/wp-admin/admin-ajax.php", headers, form)
        }

        var genre = ""
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                else -> {}
            }
        }

        if (genre.isNotEmpty()) {
            val url = if (page == 1) {
                "$baseUrl/manga-genre/$genre/"
            } else {
                "$baseUrl/manga-genre/$genre/page/$page/"
            }
            return GET(url, headers)
        }

        return popularMangaRequest(page)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.contains("admin-ajax.php")) {
            val jsonString = response.body.string()

            // Safety check to ensure we actually got JSON back
            if (!jsonString.trim().startsWith("{")) return MangasPage(emptyList(), false)

            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            val results = jsonObject["results"]?.jsonArray ?: return MangasPage(emptyList(), false)

            val mangas = results.mapNotNull { element ->
                val html = element.jsonPrimitive.content
                val doc = Jsoup.parse(html)
                val a = doc.selectFirst("a") ?: return@mapNotNull null
                val img = doc.selectFirst("img")
                val title = doc.selectFirst(".search-result-title")?.text()
                    ?: img?.attr("alt")
                    ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(a.attr("href"))
                    this.title = title
                    thumbnail_url = img?.attr("src")
                }
            }
            // Live Search does not return pagination metadata
            return MangasPage(mangas, false)
        }

        return popularMangaParse(response)
    }

    override val mangaDetailsSelectorTitle = "h1.serie-title"
    override val mangaDetailsSelectorAuthor = ".stat-item:has(.stat-label:contains(Author)) .stat-value"
    override val mangaDetailsSelectorArtist = ".stat-item:has(.stat-label:contains(Artist)) .stat-value"
    override val mangaDetailsSelectorStatus = ".stat-item:has(.stat-label:contains(Status)) .manga"
    override val mangaDetailsSelectorGenre = "div.genre-list a"
    override val mangaDetailsSelectorDescription = "div.description-content"
    override val mangaDetailsSelectorThumbnail = ".main-cover .cover"
    override val altNameSelector = "h6.alternative-title"
    override val seriesTypeSelector = ".stat-item:has(.stat-label:contains(Type)) .manga"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        manga.status = parseStatus(document.selectFirst(mangaDetailsSelectorStatus)?.text())
        return manga
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("Releasing", true) -> SManga.ONGOING
        status.contains("Completed", true) -> SManga.COMPLETED
        status.contains("On Hold", true) -> SManga.ON_HIATUS
        status.contains("Canceled", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.list-body-hh li.free-chap"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a")!!
        setUrlWithoutDomain(urlElement.absUrl("href"))
        name = element.selectFirst("zebi")?.text()?.removeSuffix(":")?.trim()
            ?: urlElement.ownText().trim().ifEmpty { urlElement.text().trim() }
    }

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = if (fromSearch) {
        url?.replace("-280x400.", ".")
    } else {
        url
    }

    override val pageListParseSelector = "div#ch-images img"

    // Custom Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Filters are ignored if you enter a text search."),
        Filter.Separator(),
        GenreFilter(),
    )

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<Select>", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josie"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Monsters", "monsters"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Regression", "regression"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Survival", "survival"),
            Pair("Tragedy", "tragedy"),
            Pair("Villainess", "villainess"),
        ),
    )
}
