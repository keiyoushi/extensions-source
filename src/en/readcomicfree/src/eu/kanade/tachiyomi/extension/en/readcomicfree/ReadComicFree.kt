package eu.kanade.tachiyomi.extension.en.readcomicfree

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

class ReadComicFree : ParsedHttpSource() {

    override val name = "ReadComicFree"

    override val baseUrl = "https://readcomicfree.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.items div.item"

    override fun latestUpdatesSelector() = "nav > ul > li.row"

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
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                var genreSegment = ""
                var statusSegment = ""
                var sortSegment = ""
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> genreSegment = if (filter.toUriPart().isNullOrBlank()) "" else "genre/${filter.toUriPart()}"
                        is StatusFilter -> statusSegment = if (filter.toUriPart().isNullOrBlank()) "" else filter.toUriPart()
                        is SortFilter -> sortSegment = if (filter.toUriPart().isNullOrBlank()) "" else filter.toUriPart()
                        else -> {}
                    }
                }
                val pathSegment = when {
                    !genreSegment.isNullOrBlank() -> {
                        when {
                            !statusSegment.isNullOrBlank() -> if (!sortSegment.isNullOrBlank()) "$genreSegment/$statusSegment/$sortSegment" else "$genreSegment/$statusSegment"
                            !sortSegment.isNullOrBlank() -> "$genreSegment/$sortSegment"
                            else -> genreSegment
                        }
                    }

                    !statusSegment.isNullOrBlank() -> if (!sortSegment.isNullOrBlank()) "status/$statusSegment/$sortSegment" else "status/$statusSegment"
                    !sortSegment.isNullOrBlank() -> "genre/$sortSegment"
                    else -> "genre"
                }
                addPathSegments(pathSegment)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        with(element.select("div.box_li .box_img")) {
            setUrlWithoutDomain(select("a").attr("href"))
            title = select("a").attr("title")
            thumbnail_url = select("a img.lazy").attr("data-original")
        }
    }

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        with(element.select("h3 a")) {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = element.select(".box_li .box_img img").attr("data-original")
    }
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        with(element.select("div.box_li .box_img")) {
            setUrlWithoutDomain(select("a").attr("href"))
            title = select("a").attr("title")
            thumbnail_url = select("a img.lazy").attr("data-original")
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel='next']"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.title-detail").text()
            thumbnail_url = document.select("div.col-image > img").attr("src")
            status = parseStatus(document.select("li.status.row > p.col-xs-8").text())
            author = document.select("li.author.row .col-xs-8 a").text()
            description = document.select(".detail-content > p").text()
            genre = document.select("li.kind.row .col-xs-8 a[href*='genre']").joinToString(separator = ", ") { it.text() }
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.list-chapter > nav > ul > li.row:has(div.chapter)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            with(element.select("div.chapter > a")) {
                setUrlWithoutDomain(attr("href"))
                name = text()
            }
            date_upload = dateParse(element.select("div.col-xs-3").text())
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
        return GET(baseUrl + chapter.url + "/all", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-chapter img").mapIndexed { index, img ->
            Page(index, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Can search either for a name or using filters, not both"),
        Filter.Separator(),
        GenreFilter(getGenreList),
        StatusFilter(getStatusList),
        SortFilter(getSortList),
    )

    private class GenreFilter(genrePairs: Array<Pair<String, String>>) : UriPartFilter("Category", genrePairs)
    private class StatusFilter(genrePairs: Array<Pair<String, String>>) : UriPartFilter("Status", genrePairs)
    private class SortFilter(genrePairs: Array<Pair<String, String>>) : UriPartFilter("Sort", genrePairs)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
    private val getStatusList = arrayOf(
        Pair("Any", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed"),
    )

    private val getSortList = arrayOf(
        Pair("Latest Update", ""), // different from newest comic, could be an older comic with an update
        Pair("Newest comic", "newest"),
        Pair("Popular", "popular"),
        Pair("Alphabetical", "alphabet"),
    )
    private val getGenreList = arrayOf(
        Pair("All genres", ""),
        Pair("Marvel", "marvel"),
        Pair("DC Comics", "dc-comics"),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Anthology", "anthology"),
        Pair("Anthropomorphic", "anthropomorphic"),
        Pair("Biography", "biography"),
        Pair("Children", "children"),
        Pair("Comedy", "comedy"),
        Pair("Crime", "crime"),
        Pair("Cyborgs", "cyborgs"),
        Pair("Dark Horse", "dark-horse"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Fantasy", "fantasy"),
        Pair("Family", "family"),
        Pair("Fighting", "fighting"),
        Pair("Gore", "gore"),
        Pair("Graphic Novels", "graphic-novels"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Leading Ladies", "leading-ladies"),
        Pair("Literature", "literature"),
        Pair("Magic", "magic"),
        Pair("Manga", "manga"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Military", "military"),
        Pair("Movie Cinematic Link", "movie-cinematic-link"),
        Pair("Mystery", "mystery"),
        Pair("Mythology", "mythology"),
        Pair("Psychological", "psychological"),
        Pair("Personal", "personal"),
        Pair("Political", "political"),
        Pair("Post-Apocalyptic", "post-apocalyptic"),
        Pair("Pulp", "pulp"),
        Pair("Robots", "robots"),
        Pair("Romance", "romance"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Science Fiction", "science-fiction"),
        Pair("Sports", "sports"),
        Pair("Spy", "spy"),
        Pair("Superhero", "superhero"),
        Pair("Supernatural", "supernatural"),
        Pair("Suspense", "suspense"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Vampires", "vampires"),
        Pair("Vertigo", "vertigo"),
        Pair("Video Games", "video-games"),
        Pair("War", "war"),
        Pair("Western", "western"),
        Pair("Zombies", "zombies"),
    )
}
