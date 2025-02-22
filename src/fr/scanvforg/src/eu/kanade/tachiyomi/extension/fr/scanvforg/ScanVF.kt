package eu.kanade.tachiyomi.extension.fr.scanvforg

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class ScanVF : ParsedHttpSource() {

    override val name = "scanvf.org"

    override val baseUrl = "https://scanvf.org"

    override val lang = "fr"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    private val dateFormat by lazy {
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?q=p&page=$page", headers)

    override fun popularMangaSelector() = "div.series"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a.link-series")!!

        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst("div.series-img-wrapper img")?.absUrl("data-src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination a.page-link[rel=next]"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga?q=u&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            val url = "/manga/$slug"
            val manga = SManga.create().apply {
                this.url = url
            }

            client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .map {
                    MangasPage(
                        listOf(
                            mangaDetailsParse(it).apply {
                                this.url = url
                            },
                        ),
                        false,
                    )
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = Jsoup.parseBodyFragment(
            json.parseToJsonElement(response.body.string()).jsonPrimitive.content,
            baseUrl,
        )

        val manga = document.select(searchMangaSelector()).map {
            searchMangaFromElement(it)
        }

        return MangasPage(manga, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.card h1")!!.text().removeSuffix(" Scan")
        author = document.select("div.card-series-detail div:contains(Auteur) div.badge").joinToString { it.text() }
        genre = document.select("div.card-series-detail div:contains(Categories) div.badge").joinToString { it.text() }
        description = document.select("main div.card div:has(h5:contains(Résumé)) p").text()
        thumbnail_url = document.selectFirst("div.series-picture-lg img")?.absUrl("src")
    }

    override fun chapterListSelector() = "div.chapters-list div.col-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val h5 = element.selectFirst("h5")!!

        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = h5.ownText()
        date_upload = try {
            dateFormat.parse(h5.selectFirst("div")!!.text())!!.time
        } catch (e: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        // This should be the URL we got from chapterListParse, i.e. /scan/:id
        // However, if there's a page number stuck onto it, we remove it first,
        // just in case.
        val url = document.location().removeSuffix("/1")
        val pageCount = findPageCount(url)

        return (1..pageCount).map {
            Page(it, "$url/$it")
        }
    }

    override fun imageUrlParse(document: Document): String =
        document.selectFirst("div.book-page img")!!.absUrl("src")

    // Disable redirects, since an out of range page request redirects us back
    // to the manga details page.
    private val pageListClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun findPageCount(url: String): Int {
        val path = url.toHttpUrl().encodedPath

        // Since there's nothing to tell us about the page count (or I'm blind),
        // we resort to good old binary search.
        //
        // The website redirects us back to the manga details page if we have
        // gone too far.
        //
        // We can't be sure of the maximum number of pages this source has
        // (sometimes stuff is uploaded in volumes) so before we begin we need
        // to find the upper bound by doubling up until we get a redirect.
        var high = 24

        while (true) {
            val (code, location) = pageListClient.newCall(GET("$url/$high", headers)).execute()
                .use {
                    it.code to it.headers["location"]
                }

            if (code == 301 || code == 302) {
                // For some reason, on the last page, the website redirects to the same URL
                // with a ?bypass=1 query parameter added.
                if (location!!.startsWith(path)) {
                    return high
                }

                break
            }

            high *= 2
        }

        // Now we begin the actual binary search.
        var low = 1
        var pageCount: Int

        while (true) {
            pageCount = low + (high - low) / 2

            val (code, location) = pageListClient.newCall(GET("$url/$pageCount", headers)).execute()
                .use {
                    it.code to it.headers["location"]
                }

            if (code == 301 || code == 302) {
                // For some reason, on the last page, the website redirects to the same URL
                // with a ?bypass=1 query parameter added.
                if (location!!.startsWith(path)) {
                    return pageCount
                }

                high = pageCount - 1
                continue
            }

            low = pageCount + 1
        }
    }

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
