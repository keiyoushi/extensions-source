package eu.kanade.tachiyomi.extension.en.readcomic

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

class ReadComic : ParsedHttpSource() {

    override val name = "ReadComic"

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
                    is GenreFilter -> addQueryParameter("wg", filter.toUriPart())
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

    private class GenreFilter(genrePairs: Array<Pair<String, String>>) : UriPartFilter("Category", genrePairs)
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
    private val getGenreList = arrayOf(
        Pair("Any", ""),
        Pair("Marvel", "Marvel"),
        Pair("DC Comics", "DC%20Comics"),
        Pair("Action", "Action"),
        Pair("Adventure", "Adventure"),
        Pair("Anthology", "Anthology"),
        Pair("Anthropomorphic", "Anthropomorphic"),
        Pair("Biography", "Biography"),
        Pair("Children", "Children"),
        Pair("Comedy", "Comedy"),
        Pair("Crime", "Crime"),
        Pair("Cyborgs", "Cyborgs"),
        Pair("Dark Horse", "Dark%20Horse"),
        Pair("Demons", "Demons"),
        Pair("Drama", "Drama"),
        Pair("Fantasy", "Fantasy"),
        Pair("Family", "Family"),
        Pair("Fighting", "Fighting"),
        Pair("Gore", "Gore"),
        Pair("Graphic Novels", "Graphic%20Novels"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Leading Ladies", "Leading%20Ladies"),
        Pair("Literature", "Literature"),
        Pair("Magic", "Magic"),
        Pair("Manga", "Manga"),
        Pair("Martial Arts", "Martial%20Arts"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Military", "Military"),
        Pair("Movie Cinematic Link", "Movie%20Cinematic%20Link"),
        Pair("Mystery", "Mystery"),
        Pair("Mythology", "Mythology"),
        Pair("Psychological", "Psychological"),
        Pair("Personal", "Personal"),
        Pair("Political", "Political"),
        Pair("Post-Apocalyptic", "Post-Apocalyptic"),
        Pair("Pulp", "Pulp"),
        Pair("Robots", "Robots"),
        Pair("Romance", "Romance"),
        Pair("Sci-Fi", "Sci-Fi"),
        Pair("Slice of Life", "Slice%20of%20Life"),
        Pair("Science Fiction", "Science%20Fiction"),
        Pair("Sports", "Sports"),
        Pair("Spy", "Spy"),
        Pair("Superhero", "Superhero"),
        Pair("Supernatural", "Supernatural"),
        Pair("Suspense", "Suspense"),
        Pair("Thriller", "Thriller"),
        Pair("Tragedy", "Tragedy"),
        Pair("Vampires", "Vampires"),
        Pair("Vertigo", "Vertigo"),
        Pair("Video Games", "Video%20Games"),
        Pair("War", "War"),
        Pair("Western", "Western"),
        Pair("Zombies", "Zombies"),
    )
}
