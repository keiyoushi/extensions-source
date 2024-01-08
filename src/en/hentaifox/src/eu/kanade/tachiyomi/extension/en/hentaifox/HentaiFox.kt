package eu.kanade.tachiyomi.extension.en.hentaifox

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HentaiFox : ParsedHttpSource() {

    override val name = "HentaiFox"

    override val baseUrl = "https://hentaifox.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return if (page == 2) {
            GET("$baseUrl/page/$page/", headers)
        } else {
            GET("$baseUrl/pag/$page/", headers)
        }
    }

    override fun popularMangaSelector() = "div.thumb"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h2 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").first()!!.attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "li.page-item:last-of-type:not(.disabled)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotEmpty()) {
            GET("$baseUrl/search/?q=$query&page=$page", headers)
        } else {
            var url = "$baseUrl/tag/"

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        url += "${filter.toUriPart()}/pag/$page/"
                    }
                    else -> {}
                }
            }
            GET(url, headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return document.select("div.gallery_top").let { info ->
            SManga.create().apply {
                title = info.select("h1").text()
                genre = info.select("ul.tags a").joinToString { it.ownText() }
                artist = info.select("ul.artists a").joinToString { it.ownText() }
                thumbnail_url = info.select("img").attr("abs:src")
                description = info.select("ul.parodies a")
                    .let { e -> if (e.isNotEmpty()) "Parodies: ${e.joinToString { it.ownText() }}\n\n" else "" }
                description += info.select("ul.characters a")
                    .let { e -> if (e.isNotEmpty()) "Characters: ${e.joinToString { it.ownText() }}\n\n" else "" }
                description += info.select("ul.groups a")
                    .let { e -> if (e.isNotEmpty()) "Groups: ${e.joinToString { it.ownText() }}\n\n" else "" }
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                // page path with a marker at the end
                url = "${response.request.url.toString().replace("/gallery/", "/g/")}#"
                // number of pages
                url += response.asJsoup().select("[id=load_pages]").attr("value")
            },
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        // split the "url" to get the page path and number of pages
        return chapter.url.split("#").let { list ->
            Observable.just(listOf(1..list[1].toInt()).flatten().map { Page(it, list[0] + "$it/") })
        }
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("img#gimg").attr("abs:data-src")
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )

    // Top 50 tags
    private class GenreFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("<select>", "---"),
            Pair("Big breasts", "big-breasts"),
            Pair("Sole female", "sole-female"),
            Pair("Sole male", "sole-male"),
            Pair("Anal", "anal"),
            Pair("Nakadashi", "nakadashi"),
            Pair("Group", "group"),
            Pair("Stockings", "stockings"),
            Pair("Blowjob", "blowjob"),
            Pair("Schoolgirl uniform", "schoolgirl-uniform"),
            Pair("Rape", "rape"),
            Pair("Lolicon", "lolicon"),
            Pair("Glasses", "glasses"),
            Pair("Defloration", "defloration"),
            Pair("Ahegao", "ahegao"),
            Pair("Incest", "incest"),
            Pair("Shotacon", "shotacon"),
            Pair("X-ray", "x-ray"),
            Pair("Bondage", "bondage"),
            Pair("Full color", "full-color"),
            Pair("Double penetration", "double-penetration"),
            Pair("Femdom", "femdom"),
            Pair("Milf", "milf"),
            Pair("Yaoi", "yaoi"),
            Pair("Multi-work series", "multi-work-series"),
            Pair("Schoolgirl", "schoolgirl"),
            Pair("Mind break", "mind-break"),
            Pair("Paizuri", "paizuri"),
            Pair("Mosaic censorship", "mosaic-censorship"),
            Pair("Impregnation", "impregnation"),
            Pair("Males only", "males-only"),
            Pair("Sex toys", "sex-toys"),
            Pair("Sister", "sister"),
            Pair("Dark skin", "dark-skin"),
            Pair("Ffm threesome", "ffm-threesome"),
            Pair("Hairy", "hairy"),
            Pair("Cheating", "cheating"),
            Pair("Sweating", "sweating"),
            Pair("Yuri", "yuri"),
            Pair("Netorare", "netorare"),
            Pair("Full censorship", "full-censorship"),
            Pair("Schoolboy uniform", "schoolboy-uniform"),
            Pair("Dilf", "dilf"),
            Pair("Big penis", "big-penis"),
            Pair("Futanari", "futanari"),
            Pair("Swimsuit", "swimsuit"),
            Pair("Collar", "collar"),
            Pair("Uncensored", "uncensored"),
            Pair("Big ass", "big-ass"),
            Pair("Story arc", "story-arc"),
            Pair("Teacher", "teacher"),
        ),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
