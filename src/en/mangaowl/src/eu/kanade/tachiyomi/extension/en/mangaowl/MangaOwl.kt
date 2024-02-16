package eu.kanade.tachiyomi.extension.en.mangaowl

import eu.kanade.tachiyomi.extension.en.mangaowl.MangaOwlFactory.Genre
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaOwl(
    private val collectionUrl: String,
    extraName: String = "",
    private val genresList: List<Genre> = listOf(),
) : ParsedHttpSource() {
    override val name: String = "MangaOwl$extraName"

    override val baseUrl = "https://mangaowl.to"
    private val searchPath = "10-search"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$collectionUrl?ordering=-view_count&page=$page", headers)
    }

    override fun popularMangaSelector() = "div.manga-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a:nth-child(2)").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("a:nth-child(1) > img").attr("src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "div.pagination > a[title='Next']"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$collectionUrl?ordering=-modified_at&page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty() || filters.isEmpty()) {
            // Search won't work together with filter
            GET("$baseUrl/$searchPath?q=$query&page=$page", headers)
        } else {
            val url = "$baseUrl/$collectionUrl".toHttpUrl().newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("ordering", filter.toUriPart())
                    }
                    is StatusFilter -> if (!filter.toUriPart().isNullOrEmpty()) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                    is GenreFilter ->
                        filter.state
                            .filter { it.state }
                            .forEach { url.addQueryParameter("genres", it.uriPart) }
                    else -> {}
                }
            }

            url.apply {
                addQueryParameter("page", page.toString())
            }
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("#main-container > div > div.row-responsive > div.flex-fill.column > div > div.flex-fill.column.section-container.p-3 > div.row-responsive.py-2")

        return SManga.create().apply {
            title = infoElement.select("div.comic-info-container > h1.story-name").text()
            infoElement.select("div.comic-info-container > div.comic-attrs > div").forEach { attr ->
                when (attr.select("span").text()) {
                    "Author" -> author = attr.select("div > a").text()
                    "Genres" -> genre = attr.select("div > a").text()
                }
            }

            description = infoElement.select("div.comic-info-container > span.story-desc").text()
            thumbnail_url = infoElement.select("img").attr("abs:src")
            infoElement.select("div:first-child > div.section-status").forEach { stt ->
                if (stt.select("span:first-child").text() == "Status") {
                    status = stt.select("span:last-child").text().let {
                        when {
                            it.contains("Ongoing") -> SManga.ONGOING
                            it.contains("Completed") -> SManga.COMPLETED
                            else -> SManga.UNKNOWN
                        }
                    }
                }
            }
        }
    }

    // Chapters

    // Only selects chapter elements with links, since sometimes chapter lists have unlinked chapters
    override fun chapterListSelector() = "div.chapters-container > a"

    private fun DateFormat.tryParse(str: String?): Long = if (str.isNullOrEmpty()) {
        assert(false) { "Date string is null or empty" }
        0L
    } else {
        runCatching {
            parse(str)?.time ?: 0L
        }.onFailure {
            assert(false) { "Cannot parse date $str: ${it.message}" }
        }.getOrDefault(0L)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(window.__NUXT__=)")
        val nuxt = script?.data() ?: ""

        val argumentsReg = Regex("\\(function\\((.*)\\)\\{")
        val valueReg = Regex("\\}\\((.*)\\)\\);")
        val arguments = argumentsReg.find(nuxt)?.groupValues?.get(1)?.split(",")
        val values = valueReg.find(nuxt)?.groupValues?.get(1)?.split(",")
        val mappedArguments = arguments?.mapIndexed { index, arg -> arg to (values?.get(index)) }?.toMap()

        val chapterListRegex = Regex("chapters:\\[(.*)],latest_chapter")
        val chapters = chapterListRegex.find(nuxt)?.groupValues?.get(1)?.split("},")

        val slugRegex = Regex("slug:\"(.*)\"")
        val slug = slugRegex.find(nuxt)?.groupValues?.get(1)
            ?: response.request.url.toString().substringAfterLast("/")

        return chapters!!.map {
            val id_ = it.substringAfter("id:").substringBefore(",created_at")
            val id = id_.toIntOrNull() ?: mappedArguments?.get(id_)?.toIntOrNull()
            var name_ = it.substringAfter("name:").substringBefore(',')
            if (name_[0] != '"') {
                name_ = mappedArguments?.get(name_) ?: "\"Chapter\""
            }
            var date = it.substringAfter("created_at:").substringBefore(',')
            if (date[0] != '"') {
                date = mappedArguments?.get(date) ?: "\"0\""
            }

            SChapter.create().apply {
                url = "$baseUrl/10-reading/$slug/$id"
                name = name_.substring(1, name_.length - 1)
                date_upload = dateFormat.tryParse(date.substring(1, date.length - 1))
            }
        }.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        return GET("https://api.mangaowl.to/v1/chapters/$id/images?page_size=1000", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val imageRegex = Regex("\"image\":\"([^\"]+)\"")
        return imageRegex.findAll(document.text()).toList().mapIndexed { i, match ->
            Page(
                index = i,
                imageUrl = match.groupValues[1],
            )
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Search query won't use filters"),
        GenreFilter(genresList),
        StatusFilter(),
        SortFilter(),
    )

    private class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Default", null),
            Pair("Most view", "-view_count"),
            Pair("Added", "created_at"),
            Pair("Last update", "-modified_at"),
            Pair("High rating", "rating"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Any", null),
            Pair("Completed", "completed"),
            Pair("Ongoing", "ongoing"),
        ),
    )

    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
