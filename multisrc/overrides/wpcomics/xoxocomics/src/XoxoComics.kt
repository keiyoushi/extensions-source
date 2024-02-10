package eu.kanade.tachiyomi.extension.en.xoxocomics

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class XoxoComics : WPComics("XOXO Comics", "https://xoxocomic.com", "en", SimpleDateFormat("MM/dd/yyyy", Locale.US), null) {
    override val searchPath = "search-comic"
    override val popularPath = "hot-comic"
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comic-update?page=$page", headers)
    override fun latestUpdatesSelector() = "li.row"
    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("data-original")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.let { if (it.isEmpty()) getFilterList() else it }
        return if (query.isNotEmpty() || filterList.isEmpty()) {
            // Search won't work together with filter
            return GET("$baseUrl/$searchPath?keyword=$query&page=$page", headers)
        } else {
            val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

            var genreFilter: UriPartFilter? = null
            var statusFilter: UriPartFilter? = null
            filterList.forEach { filter ->
                when (filter) {
                    is GenreFilter -> genreFilter = filter
                    is StatusFilter -> statusFilter = filter
                    else -> {}
                }
            }

            // Genre filter must come before status filter
            genreFilter?.toUriPart()?.let { url.addPathSegment(it) }
            statusFilter?.toUriPart()?.let { url.addPathSegment(it) }

            url.apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("sort", "0")
            }

            GET(url.toString(), headers)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // recursively add chapters from paginated chapter list
        fun parseChapters(document: Document) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select("ul.pagination a[rel=next]").firstOrNull()?.let { a ->
                parseChapters(client.newCall(GET(a.attr("abs:href"), headers)).execute().asJsoup())
            }
        }

        parseChapters(response.asJsoup())
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            date_upload = element.select("div.col-xs-3").text().toDate()
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + "${chapter.url}/all")

    override fun getStatusList(): Array<Pair<String?, String>> = arrayOf(
        Pair(null, "All"),
        Pair("ongoing", "Ongoing"),
        Pair("completed", "Completed"),
    )
    override fun getGenreList(): Array<Pair<String?, String>> = arrayOf(
        null to "All",
        "marvel-comic" to "Marvel",
        "dc-comics-comic" to "DC Comics",
        "dark-horse-comic" to "Dark Horse",
        "action-comic" to "Action",
        "adventure-comic" to "Adventure",
        "anthology-comic" to "Anthology",
        "anthropomorphic-comic" to "Anthropomorphic",
        "biography-comic" to "Biography",
        "children-comic" to "Children",
        "comedy-comic" to "Comedy",
        "crime-comic" to "Crime",
        "drama-comic" to "Drama",
        "family-comic" to "Family",
        "fantasy-comic" to "Fantasy",
        "fighting-comic" to "Fighting",
        "graphic-novels-comic" to "Graphic Novels",
        "historical-comic" to "Historical",
        "horror-comic" to "Horror",
        "leading-ladies-comic" to "Leading Ladies",
        "lgbtq-comic" to "LGBTQ",
        "literature-comic" to "Literature",
        "manga-comic" to "Manga",
        "martial-arts-comic" to "Martial Arts",
        "military-comic" to "Military",
        "mini-series-comic" to "Mini-Series",
        "movies-tv-comic" to "Movies &amp; TV",
        "music-comic" to "Music",
        "mystery-comic" to "Mystery",
        "mythology-comic" to "Mythology",
        "personal-comic" to "Personal",
        "political-comic" to "Political",
        "post-apocalyptic-comic" to "Post-Apocalyptic",
        "psychological-comic" to "Psychological",
        "pulp-comic" to "Pulp",
        "religious-comic" to "Religious",
        "robots-comic" to "Robots",
        "romance-comic" to "Romance",
        "school-life-comic" to "School Life",
        "sci-fi-comic" to "Sci-Fi",
        "slice-of-life-comic" to "Slice of Life",
        "sport-comic" to "Sport",
        "spy-comic" to "Spy",
        "superhero-comic" to "Superhero",
        "supernatural-comic" to "Supernatural",
        "suspense-comic" to "Suspense",
        "teen-comic" to "Teen",
        "thriller-comic" to "Thriller",
        "vampires-comic" to "Vampires",
        "video-games-comic" to "Video Games",
        "war-comic" to "War",
        "western-comic" to "Western",
        "zombies-comic" to "Zombies",
    )
}
