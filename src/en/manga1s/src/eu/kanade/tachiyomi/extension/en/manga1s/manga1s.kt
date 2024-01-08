package eu.kanade.tachiyomi.extension.en.manga1s

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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

class manga1s : ParsedHttpSource() {

    override val name = "Manga1s"

    override val baseUrl = "https://manga1s.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/top-search/$page", headers)

    override fun popularMangaSelector() =
        ".novel-wrap"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(element.select("h2 > a").attr("href"))
            title = element.select("h2 > a").text()
            thumbnail_url = element.select("img").attr("abs:data-src")
        }

    override fun popularMangaNextPageSelector() =
        "ul.pagination > li:last-child > a"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/last-update/$page", headers)

    override fun latestUpdatesSelector() =
        popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() =
        popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genre = filters.filterIsInstance<GenreFilter>().first().selected

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (genre.isNotEmpty()) {
                addPathSegment("genre")
                addPathSegment(genre)
            } else {
                addPathSegment("search")
                addQueryParameter("q", query)
            }
            addQueryParameter("p", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() =
        popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() =
        popularMangaNextPageSelector()

    // Genres
    abstract class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
    ) {
        val selected get() = options[state].second
    }

    class GenreFilter : SelectFilter("Genre", genres) {
        companion object {
            private val genres = listOf(
                Pair("<select>", ""),
                Pair("Romance", "romance"),
                Pair("Comedy", "comedy"),
                Pair("Fantasy", "fantasy"),
                Pair("Drama", "drama"),
                Pair("Action", "action"),
                Pair("Adventure", "adventure"),
                Pair("Slice of Life", "slice-of-life"),
                Pair("Shounen", "shounen"),
                Pair("Supernatural", "supernatural"),
                Pair("Seinen", "seinen"),
                Pair("School Life", "school-life"),
                Pair("Shoujo", "shoujo"),
                Pair("Historical", "historical"),
                Pair("Ecchi", "ecchi"),
                Pair("Webtoon", "webtoon"),
                Pair("Harem", "harem"),
                Pair("Mystery", "mystery"),
                Pair("Martial Arts", "martial-arts"),
                Pair("Psychological", "psychological"),
                Pair("Yaoi", "yaoi"),
                Pair("Horror", "horror"),
                Pair("Manhwa", "manhwa"),
                Pair("Full Color", "full-color"),
                Pair("Josei", "josei"),
                Pair("Oneshot", "oneshot"),
                Pair("Mature", "mature"),
                Pair("Magic", "magic"),
                Pair("Isekai", "isekai"),
                Pair("Adult", "adult"),
                Pair("Yuri", "yuri"),
                Pair("Tragedy", "tragedy"),
                Pair("Sports", "sports"),
                Pair("Shoujo ai", "shoujo-ai"),
                Pair("Shounen ai", "shounen-ai"),
                Pair("Smut", "smut"),
                Pair("Sci-Fi", "sci-fi"),
                Pair("Doujinshi", "doujinshi"),
                Pair("Gender Bender", "gender-bender"),
                Pair("Demons", "demons"),
                Pair("Adaptation", "adaptation"),
                Pair("Reincarnation", "reincarnation"),
                Pair("Manhua", "manhua"),
                Pair("Thriller", "thriller"),
                Pair("Mecha", "mecha"),
                Pair("Game", "game"),
                Pair("Super Power", "super-power"),
                Pair("Military", "military"),
                Pair("Music", "music"),
                Pair("Monsters", "monsters"),
                Pair("Office Workers", "office-workers"),
            )
        }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filters ignore text search"),
        Filter.Separator(),
        GenreFilter(),
    )

    // Details
    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            title = document.select(".novel-name > h1").text()
            author = document.select(".novel-authors a").text()
            description = document.select("#manga-description").text().trim()
            genre = document.select(".novel-categories > a").joinToString { it.text() }
            status =
                when (
                    document.select(".novel-info i.fa-flag")[0].parent()!!.parent()!!.select("span")
                        .text()
                ) {
                    "On-going" -> SManga.ONGOING
                    "Completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            thumbnail_url = document.select(".novel-thumbnail > img").attr("abs:data-src")
        }

    // Chapters
    override fun chapterListSelector() =
        ".chapter-name a"

    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.text()
            chapter_number = element.text()
                .substringAfter(" ").toFloatOrNull() ?: -1f
        }

    // Pages
    override fun pageListParse(document: Document): List<Page> =
        document.select(".chapter-detail > img[data-src]").mapIndexed { index, element ->
            Page(index, "", element.attr("abs:data-src"))
        }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used")
}
