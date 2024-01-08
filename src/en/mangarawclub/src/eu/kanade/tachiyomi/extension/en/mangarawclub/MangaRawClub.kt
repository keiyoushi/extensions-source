package eu.kanade.tachiyomi.extension.en.mangarawclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaRawClub : ParsedHttpSource() {

    override val id = 734865402529567092
    override val name = "MangaGeko"
    override val baseUrl = "https://www.mangageko.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val altName = "Alternative Name:"

        private val DATE_FORMATTER by lazy { SimpleDateFormat("MMMMM dd, yyyy, h:mm a", Locale.ENGLISH) }
        private val DATE_FORMATTER_2 by lazy { SimpleDateFormat("MMMMM dd, yyyy, h a", Locale.ENGLISH) }
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse-comics/?results=$page&filter=views", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/jumbo/manga/?results=$page", headers)
    }

    override fun searchMangaSelector() = "ul.novel-list > li.novel-item"
    override fun popularMangaSelector() = searchMangaSelector()
    override fun latestUpdatesSelector() = "ul.novel-list.chapters > li.novel-item"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.title = element.select(".novel-title").first()?.text() ?: ""
        manga.thumbnail_url = element.select(".novel-cover img").attr("abs:data-src")
        manga.setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
        return manga
    }
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "ul.pagination > li:last-child > a"
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        if (document.select(".novel-header").first() == null) {
            throw Exception("Page not found")
        }

        val manga = SManga.create()
        val author = document.select(".author a").first()?.attr("title")?.trim() ?: ""
        if (author.lowercase(Locale.ROOT) != "updating") {
            manga.author = author
        }

        var description = document.select(".description").first()?.text() ?: ""
        description = description.substringAfter("Summary is").trim()

        val otherTitle = document.select(".alternative-title").first()?.text()?.trim() ?: ""
        if (otherTitle.isNotEmpty() && otherTitle.lowercase(Locale.ROOT) != "updating") {
            description += "\n\n$altName $otherTitle"
        }
        manga.description = description.trim()

        manga.genre = document.select(".categories a[href*=genre]").joinToString(", ") {
            it.attr("title").removeSuffix("Genre").trim()
                .split(" ").joinToString(" ") { char ->
                    char.lowercase().replaceFirstChar { c -> c.uppercase() }
                }
        }

        val statusElement = document.select("div.header-stats")
        manga.status = when {
            statusElement.select("strong.completed").isNotEmpty() -> SManga.COMPLETED
            statusElement.select("strong.ongoing").isNotEmpty() -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        val coverElement = document.select(".cover img")
        manga.thumbnail_url = when {
            coverElement.attr("data-src").isNotEmpty() -> coverElement.attr("data-src")
            else -> coverElement.attr("src")
        }
        return manga
    }

    override fun chapterListSelector() = "ul.chapter-list > li"

    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl + manga.url + "all-chapters/"
        return GET(url, headers)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("a").attr("href"))

        val name = element.select(".chapter-title").text().removeSuffix("-eng-li")
        chapter.name = "Chapter $name"
        val number = parseChapterNumber(name)
        if (number != null) {
            chapter.chapter_number = number
        }
        val date = parseChapterDate(element.select(".chapter-update").attr("datetime"))
        chapter.date_upload = date
        return chapter
    }

    private fun parseChapterDate(string: String): Long {
        // "April 21, 2021, 4:05 p.m."
        val date = string.replace(".", "").replace("Sept", "Sep")
        return runCatching { DATE_FORMATTER.parse(date)?.time }.getOrNull()
            ?: runCatching { DATE_FORMATTER_2.parse(date)?.time }.getOrNull() ?: 0L
    }

    private fun parseChapterNumber(string: String): Float? {
        if (string.isEmpty()) {
            return null
        }
        return string.split("-")[0].toFloatOrNull()
            ?: string.split(".")[0].toFloatOrNull()
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".page-in img[onerror]").forEachIndexed { i, it ->
            pages.add(Page(i, imageUrl = it.attr("src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            // Query search
            return GET("$baseUrl/search/?search=$query", headers)
        }

        // Filter search
        val url = "$baseUrl/browse-comics/".toHttpUrlOrNull()!!.newBuilder()
        val requestBody = FormBody.Builder()
        url.addQueryParameter("results", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenrePairList -> url.addQueryParameter("genre", filter.toUriPart()) // GET
                is Order -> url.addQueryParameter("filter", filter.toUriPart()) // GET
                is Status -> requestBody.add("status", filter.toUriPart()) // POST
                is Action -> requestBody.add("action", filter.toUriPart()) // POST
                is GenreList -> { // POST
                    filter.state.filter { it.state == 1 }.forEach {
                        requestBody.add("options[]", it.name)
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
        // return POST("$baseUrl/search", headers, requestBody.build()) // csrfmiddlewaretoken required
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        Order(),
        GenrePairList(),
        // Action(),
        // Status(),
        // GenreList(getGenreList())
    )

    private class Action : UriPartFilter(
        "Action",
        arrayOf(
            Pair("All", ""),
            Pair("Include", "include"),
            Pair("Exclude", "exclude"),
        ),
    )

    private class Order : UriPartFilter(
        "Order",
        arrayOf(
            Pair("Random", "Random"),
            Pair("Updated", "Updated"),
            Pair("New", "New"),
            Pair("Views", "views"),
        ),
    )

    private class Status : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Completed", "Completed"),
            Pair("Ongoing", "Ongoing"),
        ),
    )

    private class GenrePairList : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("All", ""),
            Pair("R-18", "R-18"),
            Pair("Action", "Action"),
            Pair("Adult", "Adult"),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Cooking", "Cooking"),
            Pair("Doujinshi", "Doujinshi"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Gender bender", "Gender bender"),
            Pair("Harem", "Harem"),
            Pair("Historical", "Historical"),
            Pair("Horror", "Horror"),
            Pair("Isekai", "Isekai"),
            Pair("Josei", "Josei"),
            Pair("Ladies", "ladies"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("Martial arts", "Martial arts"),
            Pair("Mature", "Mature"),
            Pair("Mecha", "Mecha"),
            Pair("Medical", "Medical"),
            Pair("Mystery", "Mystery"),
            Pair("One shot", "One shot"),
            Pair("Psychological", "Psychological"),
            Pair("Romance", "Romance"),
            Pair("School life", "School life"),
            Pair("Sci fi", "Sci fi"),
            Pair("Seinen", "Seinen"),
            Pair("Shoujo", "Shoujo"),
            Pair("Shounen", "Shounen"),
            Pair("Slice of life", "Slice of life"),
            Pair("Sports", "Sports"),
            Pair("Supernatural", "Supernatural"),
            Pair("Tragedy", "Tragedy"),
            Pair("Webtoons", "Webtoons"),
        ),
    )

    private class Genre(name: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres+", genres)

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
