package eu.kanade.tachiyomi.extension.ja.yanmaga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable

class YanmagaComics : Yanmaga("search-item-category--comics") {

    override val name = "ヤンマガ（マンガ）"

    override val supportsLatest = true

    private lateinit var directory: Elements

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        directory = document.select(popularMangaSelector())
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val endRange = minOf(page * 24, directory.size)
        val manga = directory.subList((page - 1) * 24, endRange).map { popularMangaFromElement(it) }
        val hasNextPage = endRange < directory.lastIndex

        return MangasPage(manga, hasNextPage)
    }

    override fun popularMangaSelector() = "a.ga-comics-book-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst(".mod-book-title")!!.text()
        thumbnail_url = element.selectFirst(".mod-book-image img")?.absUrl("data-src")
    }

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    private var latestUpdatesCsrfToken: String? = null
    private var latestUpdatesMoreUrl: String? = null
    private var latestUpdatesCount: Int = 0

    override fun latestUpdatesRequest(page: Int): Request {
        val pageUrl = "$baseUrl/comics/series/newer"

        if (page == 1) {
            return GET(pageUrl, headers)
        }

        val offset = (page - 1) * LATEST_UPDATES_PER_PAGE
        val headers = headers.newBuilder()
            .set("Referer", pageUrl)
            .set("X-CSRF-Token", latestUpdatesCsrfToken!!)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET("${latestUpdatesMoreUrl!!}?offset=$offset", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val pageUrl = "$baseUrl/comics/series/newer"
        val url = response.request.url

        return if (url.pathSegments.last() == "newer") {
            val document = response.asJsoup()

            latestUpdatesCsrfToken = document.selectFirst("meta[name=csrf-token]")!!.attr("content")
            document.selectFirst(".newer-older-episode-more-button[data-count][data-path]")!!.let {
                latestUpdatesMoreUrl = it.attr("abs:data-path")
                latestUpdatesCount = it.attr("data-count").toInt()
            }

            val manga = document.select(latestUpdatesSelector())
                .map { latestUpdatesFromElement(it) }
            val hasNextPage = latestUpdatesCount > LATEST_UPDATES_PER_PAGE

            MangasPage(manga, hasNextPage)
        } else {
            val offset = url.queryParameter("offset")!!.toInt()
            val manga = parseInsertAdjacentHtmlScript(response.body.string())
                .map { latestUpdatesFromElement(Jsoup.parseBodyFragment(it, pageUrl)) }
            val hasNextPage = offset + LATEST_UPDATES_PER_PAGE < latestUpdatesCount

            MangasPage(manga, hasNextPage)
        }
    }

    override fun latestUpdatesSelector() = "#comic-episodes-newer > div"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".text-wrapper h2")!!.text()
        thumbnail_url = element.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")
    }

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".detailv2-outline-title")!!.text()
        author = document.select(".detailv2-outline-author-item a").joinToString { it.text() }
        description = document.selectFirst(".detailv2-description")?.text()
        genre = document.select(".detailv2-tag .ga-tag").joinToString { it.text() }
        thumbnail_url = document.selectFirst(".detailv2-thumbnail-image img")?.absUrl("src")
        status = if (document.selectFirst(".detailv2-link-note") != null) {
            SManga.ONGOING
        } else {
            SManga.COMPLETED
        }
    }
}

private const val LATEST_UPDATES_PER_PAGE = 12
