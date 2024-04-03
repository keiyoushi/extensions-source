package eu.kanade.tachiyomi.extension.en.readcomictop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ReadComicTop : ParsedHttpSource() {

    override val name = "ReadComic.Top"

    override val baseUrl = "https://readcomic.top"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.eg-box"

    override fun latestUpdatesSelector() = "ul.line-list"

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/popular-comics".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/comic-updates".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/advanced-search".toHttpUrl().newBuilder().apply {
            addQueryParameter("key", query)
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        addQueryParameter("wg", filter.included.joinToString("%2C"))
                        addQueryParameter("wog", filter.excluded.joinToString("%2C"))
                    }
                    is StatusFilter -> if (filter.toUriPart().isNotBlank()) {
                        addQueryParameter("status", filter.toUriPart())
                    }
                    else -> {}
                }
            }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.egb-right > a.egb-serie").attr("href"))
        title = element.select("div.egb-right > a.egb-serie").text()
        thumbnail_url = element.select("a.eg-image > img").attr("src")
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        with(element.select("ul.line-list > li > a.big-link")) {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = "https://fakeimg.pl/200x300/?text=No%20Cover&font_size=62"
    }
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        with(element.select("div.dlb-right > a.dlb-title")) {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = element.select("a.dlb-image > img").attr("src")
    }

    override fun popularMangaNextPageSelector() = "div.general-nav > a:contains(Next)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = "div.dl-box"

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.title").text()
            thumbnail_url = document.select("div.anime-image > img").attr("src")
            status = parseStatus(document.select("ul.anime-genres li.status").text())
            author = document.select("td:contains(Author:) + td").text()
            description = document.select(".detail-desc-content > p").text()
            genre = document.select("ul.anime-genres > li > a[href*='genre']").joinToString { it.text() }
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.basic-list > li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            with(element.select("a.ch-name")) {
                setUrlWithoutDomain(attr("href"))
                name = text()
            }
            date_upload = dateParse(element.select("span").text())
        }
    }

    private val dateFormat by lazy { SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH) }

    private fun dateParse(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url + "/full", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.chapter-container img").mapIndexed { index, img ->
            Page(index, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Note: can't leave both filters as Any with a blank search string"),
        Filter.Separator(),
        GenreFilter(getGenreList),
        StatusFilter(getStatusList),
    )

    private class Genre(name: String, val toUriPart: String) : Filter.TriState(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.toUriPart }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.toUriPart }
    }
    private class StatusFilter(statusPairs: Array<Pair<String, String>>) : UriPartFilter("Status", statusPairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
    private val getStatusList = arrayOf(
        Pair("Any", ""), // You might want an option for any status
        Pair("Ongoing", "ONG"),
        Pair("Completed", "CMP"),
    )
    private val getGenreList = listOf(
        Genre("Any", ""),
        Genre("Marvel", "Marvel"),
        Genre("DC Comics", "DC%20Comics"),
        Genre("Action", "Action"),
        Genre("Adventure", "Adventure"),
        Genre("Anthology", "Anthology"),
        Genre("Anthropomorphic", "Anthropomorphic"),
        Genre("Biography", "Biography"),
        Genre("Children", "Children"),
        Genre("Comedy", "Comedy"),
        Genre("Crime", "Crime"),
        Genre("Cyborgs", "Cyborgs"),
        Genre("Dark Horse", "Dark%20Horse"),
        Genre("Demons", "Demons"),
        Genre("Drama", "Drama"),
        Genre("Fantasy", "Fantasy"),
        Genre("Family", "Family"),
        Genre("Fighting", "Fighting"),
        Genre("Gore", "Gore"),
        Genre("Graphic Novels", "Graphic%20Novels"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Leading Ladies", "Leading%20Ladies"),
        Genre("Literature", "Literature"),
        Genre("Magic", "Magic"),
        Genre("Manga", "Manga"),
        Genre("Martial Arts", "Martial%20Arts"),
        Genre("Mature", "Mature"),
        Genre("Mecha", "Mecha"),
        Genre("Military", "Military"),
        Genre("Movie Cinematic Link", "Movie%20Cinematic%20Link"),
        Genre("Mystery", "Mystery"),
        Genre("Mythology", "Mythology"),
        Genre("Psychological", "Psychological"),
        Genre("Personal", "Personal"),
        Genre("Political", "Political"),
        Genre("Post-Apocalyptic", "Post-Apocalyptic"),
        Genre("Pulp", "Pulp"),
        Genre("Robots", "Robots"),
        Genre("Romance", "Romance"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Slice of Life", "Slice%20of%20Life"),
        Genre("Science Fiction", "Science%20Fiction"),
        Genre("Sports", "Sports"),
        Genre("Spy", "Spy"),
        Genre("Superhero", "Superhero"),
        Genre("Supernatural", "Supernatural"),
        Genre("Suspense", "Suspense"),
        Genre("Thriller", "Thriller"),
        Genre("Tragedy", "Tragedy"),
        Genre("Vampires", "Vampires"),
        Genre("Vertigo", "Vertigo"),
        Genre("Video Games", "Video%20Games"),
        Genre("War", "War"),
        Genre("Western", "Western"),
        Genre("Zombies", "Zombies"),
    )
}
