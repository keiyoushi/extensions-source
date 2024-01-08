package eu.kanade.tachiyomi.extension.en.readmangatoday

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

class Readmangatoday : ParsedHttpSource() {

    override val id: Long = 8

    override val name = "ReadMangaToday"

    override val baseUrl = "https://www.readmng.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient get() = network.cloudflareClient

    private val json: Json by injectLazy()

    /**
     * Search only returns data with user-agent and x-requeted-with set
     * Referer needed due to some chapters linking images from other domains
     */
    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
        add("X-Requested-With", "XMLHttpRequest")
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/hot-manga/$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-releases/$page", headers)
    }

    override fun popularMangaSelector() = "div.categoryContent > div.galeriContent > div.mangaSliderCard"

    override fun latestUpdatesSelector() = "div.listUpdates > div.miniListCard"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.selectFirst("h2")?.let {
            manga.title = it.text()
        }
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "div.categoryContent a.page-link:contains(»)"

    override fun latestUpdatesNextPageSelector() = "div.popularToday a.page-link:contains(»)"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = okhttp3.FormBody.Builder()
        builder.add("manga-name", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is TextField -> builder.add(filter.key, filter.state)
                is Type -> builder.add("type", arrayOf("all", "japanese", "korean", "chinese")[filter.state])
                is Status -> builder.add("status", arrayOf("both", "completed", "ongoing")[filter.state])
                is GenreList -> filter.state.forEach { genre ->
                    when (genre.state) {
                        Filter.TriState.STATE_INCLUDE -> builder.add("include[]", genre.id.toString())
                        Filter.TriState.STATE_EXCLUDE -> builder.add("exclude[]", genre.id.toString())
                    }
                }
                else -> {}
            }
        }
        return POST("$baseUrl/advanced-search", headers, builder.build())
    }

    override fun searchMangaSelector() = "div.mangaSliderCard"

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = "div.next-page > a.next"

    override fun mangaDetailsParse(document: Document): SManga {
        val detailElement = document.select("div.productDetail").first()!!
        val genreElement = detailElement.select("b:contains(Genres)+span.mgen>a")

        val manga = SManga.create()
        manga.author = detailElement.select("div.productRight div.infox div.flex-wrap b:contains(Author)+span>a").first()?.text()
        manga.artist = detailElement.select("div.productRight div.infox div.flex-wrap b:contains(Artist)+span>a").first()?.text()
        manga.description = detailElement.select("div.productRight div.infox h2:contains(Description)~p:eq(2)").first()?.text()
        manga.status = detailElement.select("div.imptdt:contains(Status)>i").first()?.text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = detailElement.select("div.thumb img").first()?.attr("src")
        manga.genre = genreElement.joinToString(", ") { it.text() }

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div#chapters-tabContent div.cardFlex div.checkBoxCard"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.ownText()
        chapter.date_upload = element.select("i.upload-date").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")

        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val calendar = Calendar.getInstance()

            when {
                dateWords[1].contains("Minute", true) -> {
                    calendar.add(Calendar.MINUTE, -timeAgo)
                }
                dateWords[1].contains("Hour", true) -> {
                    calendar.add(Calendar.HOUR_OF_DAY, -timeAgo)
                }
                dateWords[1].contains("Day", true) -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Week", true) -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Month", true) -> {
                    calendar.add(Calendar.MONTH, -timeAgo)
                }
                dateWords[1].contains("year", true) -> {
                    calendar.add(Calendar.YEAR, -timeAgo)
                }
            }

            return calendar.timeInMillis
        }

        return 0L
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/${chapter.url}/all-pages", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val docString = document.toString()
        val imageListRegex = Regex("\\\"images.*?:.*?(\\[.*?\\])")
        val imageListJson = imageListRegex.find(docString)!!.destructured.toList()[0]

        val imageList = json.parseToJsonElement(imageListJson).jsonArray
        val baseResolver = baseUrl.toHttpUrl()

        val scriptPages = imageList.mapIndexed { i, jsonEl ->
            val imageUrl = jsonEl.jsonPrimitive.content
            Page(i, "", baseResolver.resolve(imageUrl).toString())
        }

        return scriptPages.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    private class Status : Filter.TriState("Completed")
    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class TextField(name: String, val key: String) : Filter.Text(name)
    private class Type : Filter.Select<String>("Type", arrayOf("All", "Japanese Manga", "Korean Manhwa", "Chinese Manhua"))
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    override fun getFilterList() = FilterList(
        TextField("Author", "author-name"),
        TextField("Artist", "artist-name"),
        Type(),
        Status(),
        GenreList(getGenreList()),
    )

    // [...document.querySelectorAll("ul.manga-cat span")].map(el => `Genre("${el.nextSibling.textContent.trim()}", ${el.getAttribute('data-id')})`).join(',\n')
    // https://www.readmng.com/advanced-search
    private fun getGenreList() = listOf(
        Genre("Action", 2),
        Genre("Adventure", 4),
        Genre("Comedy", 5),
        Genre("Doujinshi", 6),
        Genre("Drama", 7),
        Genre("Ecchi", 8),
        Genre("Fantasy", 9),
        Genre("Gender Bender", 10),
        Genre("Harem", 11),
        Genre("Historical", 12),
        Genre("Horror", 13),
        Genre("Josei", 14),
        Genre("Lolicon", 15),
        Genre("Martial Arts", 16),
        Genre("Mature", 17),
        Genre("Mecha", 18),
        Genre("Mystery", 19),
        Genre("One shot", 20),
        Genre("Psychological", 21),
        Genre("Romance", 22),
        Genre("School Life", 23),
        Genre("Sci-fi", 24),
        Genre("Seinen", 25),
        Genre("Shotacon", 26),
        Genre("Shoujo", 27),
        Genre("Shoujo Ai", 28),
        Genre("Shounen", 29),
        Genre("Shounen Ai", 30),
        Genre("Slice of Life", 31),
        Genre("Smut", 32),
        Genre("Sports", 33),
        Genre("Supernatural", 34),
        Genre("Tragedy", 35),
        Genre("Yaoi", 36),
        Genre("Yuri", 37),
    )
}
