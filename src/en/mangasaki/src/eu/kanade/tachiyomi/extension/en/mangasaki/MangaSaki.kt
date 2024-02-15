package eu.kanade.tachiyomi.extension.en.mangasaki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSaki : ParsedHttpSource() {

    override val name = "MangaSaki"

    override val baseUrl = "https://www.mangasaki.org"

    override val lang = "en"

    override val supportsLatest = true

    private var searchMode: Boolean = false

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return if (page >= 1) {
            GET("$baseUrl/directory/hot?page=${page - 1}/", headers)
        } else {
            GET("$baseUrl/directory/hot?page=0", headers)
        }
    }

    override fun popularMangaSelector() = ".directory_list tbody tr"

    override fun popularMangaFromElement(element: Element): SManga {
        val Manga = SManga.create()
        val titleElement = element.select("td a img").first()!!
        Manga.title = titleElement.attr("title")
        Manga.setUrlWithoutDomain(element.select("td a").first()!!.attr("href"))
        Manga.thumbnail_url = titleElement.attr("src")

        return Manga
    }

    override fun popularMangaNextPageSelector() = "li.pager-next a"

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return if (page >= 1) {
            GET("$baseUrl/directory/new?page=${page - 1}/", headers)
        } else {
            GET("$baseUrl/directory/new?page=0", headers)
        }
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            searchMode = true
            GET("$baseUrl/search/node/$query?page=${page - 1}", headers)
        } else {
            searchMode = false
            var url = "$baseUrl/tags/"
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        url += "${filter.toUriPart()}?page=${page - 1}"
                    }
                    else -> {}
                }
            }
            GET(url, headers)
        }
    }

    override fun searchMangaSelector(): String {
        return if (!searchMode) {
            "div.view-content div.views-row"
        } else {
            "ol.search-results li.search-result"
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val Manga = SManga.create()
        if (!searchMode) {
            Manga.title = element.select("div.views-field-title a").text()
            Manga.setUrlWithoutDomain(element.select("div.views-field-title a").attr("href"))
            Manga.thumbnail_url = element.select("div.views-field-field-image2 img").attr("src")
        } else {
            // This part needs to be fixed
            val titleElement = element.select("h3.title a")
            Manga.title = titleElement.text()
            Manga.setUrlWithoutDomain(titleElement.attr("href"))
        }

        return Manga
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val Manga = SManga.create()
        Manga.author = document.select("div.field-name-field-author div.field-item").first()?.text()
        Manga.genre = document.select("div.field-name-field-genres ul li a").joinToString { it.text() }
        Manga.description = document.select("div.field-name-body div.field-item p").text()
        Manga.thumbnail_url = document.select("div.field-name-field-image2 div.field-item img").attr("src")

        document.select("div.field-name-field-status div.field-item").text().also { statusText ->
            when {
                statusText.contains("Ongoing", true) -> Manga.status = SManga.ONGOING
                statusText.contains("Complete", true) -> Manga.status = SManga.COMPLETED
                else -> Manga.status = SManga.UNKNOWN
            }
        }

        return Manga
    }

    // chapters
    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(url: String, page: Int): Request {
        return GET("$baseUrl$url?page=${page - 1}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map(::chapterFromElement).toMutableList()
        var nextPage = 2

        while (document.select(paginationNextPageSelector).isNotEmpty()) {
            val dirtyPage = document.select("div#block-search-form form#search-block-form").attr("action")
            val cleaningIndex = dirtyPage.lastIndexOf("?")
            val cleanPage = dirtyPage.substring(0, cleaningIndex)
            document = client.newCall(chapterListRequest(cleanPage, nextPage)).execute().asJsoup()
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
            nextPage++
        }

        return chapters
    }

    private val paginationNextPageSelector = latestUpdatesNextPageSelector()

    override fun chapterListSelector() = ".chlist tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val Chapter = SChapter.create()
        Chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        Chapter.name = element.select("a").text()
        Chapter.date_upload = element.select("td").last()?.text()?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).parse(it)?.time ?: 0
        } ?: 0

        return Chapter
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        val pageList: MutableList<Page> = mutableListOf()
        val jsonString = document.select("script:containsData(showmanga)").html().removePrefix("<!--//--><![CDATA[//><!--\njQuery.extend(Drupal.settings, ").removeSuffix(");\n//--><!]]>")
        val links = parseJSON(jsonString)
        var pageNumber = 0
        links?.forEach {
            pageList.add(Page(pageNumber, "", it))
            pageNumber++
        }

        return pageList.sortedBy { page -> page.index }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        MangaSaki.GenreFilter(),
    )

    private class GenreFilter : MangaSaki.UriPartFilter(
        "Category",
        arrayOf(
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Crime", "crime"),
            Pair("Drama", "drama"),
            Pair("Dungeons", "dungeons"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("GenderBender", "genderbender"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Lolicon", "lolicon"),
            Pair("Magical Girls", "magical-girls"),
            Pair("MartialArts", "martialarts"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("N/A", "na"),
            Pair("Philosophical", "philosophical"),
            Pair("Psychological", "psychological"),
            Pair("SchoolLife", "schoollife"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Sci-fi Shounen", "sci-fi-shounen"),
            Pair("Seinen", "seinen"),
            Pair("Shotacon", "shotacon"),
            Pair("Shoujo", "shoujo"),
            Pair("ShoujoAi", "shoujoai"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("ShounenAi", "shounenai"),
            Pair("Shounen-Ai", "shounen-ai"),
            Pair("SliceofLife", "slicelife"),
            Pair("Slice of Life", "slice-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Superhero", "superhero"),
            Pair("Supernatural", "supernatural"),
            Pair("System", "system"),
            Pair("Thriller", "thriller"),
            Pair("Tragedy", "tragedy"),
            Pair("Webtoons", "webtoons"),
            Pair("Wuxia", "wuxia"),
            Pair("Yuri", "yuri"),
        ),
    )

    private fun parseJSON(jsonString: String): List<String>? {
        val jsonData = Json { ignoreUnknownKeys = true }.decodeFromString<JSONData>(jsonString)
        return jsonData.showmanga.paths.filter { it.contains("mangasaki") }
    }

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    @Serializable
    data class JSONData(val showmanga: ShowMangaData)

    @Serializable
    data class ShowMangaData(val paths: List<String>, val count_p: Int)
}
