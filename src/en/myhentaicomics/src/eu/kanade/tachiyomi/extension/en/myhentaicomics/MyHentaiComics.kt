package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MyHentaiComics : ParsedHttpSource() {

    override val name = "MyHentaiComics"

    override val baseUrl = "https://myhentaicomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/index.php/tag/2402?page=$page", headers)
    }

    override fun popularMangaSelector() = "li.g-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h2").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.ui-state-default span.ui-icon-seek-next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/index.php/?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/index.php/search?q=$query&page=$page", headers)
        } else {
            var url = baseUrl
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreFilter -> url += filter.toUriPart() + "?page=$page"
                    else -> {}
                }
            }
            GET(url, headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h2, p").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val tags = document.select("div.g-description a").partition { tag ->
            tag.text().startsWith("Artist: ")
        }
        return SManga.create().apply {
            artist = tags.first.joinToString { it.text().substringAfter(" ") }
            author = artist
            genre = tags.second.joinToString { it.text() }
            thumbnail_url = document.select("img.g-thumbnail").first()!!.attr("abs:src").replace("/thumbs/", "/resizes/")
        }
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                },
            ),
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        // recursively parse paginated pages
        fun parsePage(document: Document) {
            document.select("img.g-thumbnail").map { img ->
                pages.add(Page(pages.size, "", img.attr("abs:src").replace("/thumbs/", "/resizes/")))
            }
            document.select("ul.g-paginator a.ui-state-default:contains(Next)").firstOrNull()?.let { a ->
                parsePage(client.newCall(GET(a.attr("abs:href"), headers)).execute().asJsoup())
            }
        }

        parsePage(document)
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Cannot combine search types!"),
        Filter.Separator("-----------------"),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("<Choose a genre>", ""),
            Pair("3D", "/index.php/tag/2403"),
            Pair("Asian", "/index.php/tag/2404"),
            Pair("Ass Expansion", "/index.php/tag/2405"),
            Pair("BBW", "/index.php/tag/2406"),
            Pair("Beastiality", "/index.php/tag/2407"),
            Pair("Bisexual", "/index.php/tag/2408"),
            Pair("Body Swap", "/index.php/tag/2410"),
            Pair("Breast Expansion", "/index.php/tag/2413"),
            Pair("Bukakke", "/index.php/tag/2412"),
            Pair("Cheating", "/index.php/tag/2414"),
            Pair("Crossdressing", "/index.php/tag/2415"),
            Pair("Femdom", "/index.php/tag/2417"),
            Pair("Furry", "/index.php/tag/2418"),
            Pair("Futanari", "/index.php/tag/2419"),
            Pair("Futanari On Male", "/index.php/tag/2430"),
            Pair("Gangbang", "/index.php/tag/2421"),
            Pair("Gay", "/index.php/tag/2422"),
            Pair("Gender Bending", "/index.php/tag/2423"),
            Pair("Giantess", "/index.php/tag/2424"),
            Pair("Gloryhole", "/index.php/tag/2425"),
            Pair("Hardcore", "/index.php/tag/2426"),
            Pair("Harem", "/index.php/tag/2427"),
            Pair("Incest", "/index.php/tag/2450"),
            Pair("Interracial", "/index.php/tag/2409"),
            Pair("Lactation", "/index.php/tag/2428"),
            Pair("Lesbian", "/index.php/tag/3167"),
            Pair("Milf", "/index.php/tag/2431"),
            Pair("Mind Control & Hypnosis", "/index.php/tag/2432"),
            Pair("Muscle Girl", "/index.php/tag/2434"),
            Pair("Pegging", "/index.php/tag/2437"),
            Pair("Pregnant", "/index.php/tag/2438"),
            Pair("Rape", "/index.php/tag/2433"),
            Pair("Strap-On", "/index.php/tag/2441"),
            Pair("Superheroes", "/index.php/tag/2443"),
            Pair("Tentacles", "/index.php/tag/2444"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
