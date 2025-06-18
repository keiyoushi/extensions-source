package eu.kanade.tachiyomi.extension.all.comicgrowl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicGrowl(
    override val lang: String = "all",
    override val baseUrl: String = "https://comic-growl.com",
    override val name: String = "コミックグロウル",
    override val supportsLatest: Boolean = true,
) : ParsedHttpSource() {

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(ImageDescrambler::interceptor)
        .build()

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().set("Referer", "$baseUrl/")
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking/manga", headers)

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaSelector() = ".ranking-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            title = element.selectFirst(".title-text")!!.text()
            setImageUrlFromElement(element)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst(".series-h-info")!!
        val authorElements = infoElement.select(".series-h-credit-user-item .article-text")
        val updateDateElement = infoElement.selectFirst(".series-h-tag-label")
        return SManga.create().apply {
            title = infoElement.selectFirst("h1 > span:not(.g-hidden)")!!.text()
            author = authorElements.joinToString { it.text() }
            description = infoElement.selectFirst(".series-h-credit-info-text-text p")?.wholeText()?.trim()
            setImageUrlFromElement(document.selectFirst(".series-h-img"))
            status = if (updateDateElement != null) SManga.ONGOING else SManga.COMPLETED
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/list", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapIndexed { index, element ->
            chapterFromElement(element).apply {
                chapter_number = index.toFloat()
                if (url.isEmpty()) { // need login, set a dummy url and append lock icon for chapter name
                    val hasLockElement = element.selectFirst(".g-payment-article.wait-free-enabled")
                    url = response.request.url.newBuilder().fragment("$index-$DUMMY_URL_SUFFIX").build().toString()
                    name = (if (hasLockElement != null) LOCK_ICON else PAY_ICON) + name
                }
            }
        }
    }

    override fun chapterListSelector() = ".article-ep-list-item-img-link"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("data-href"))
            name = element.selectFirst(".series-ep-list-item-h-text")!!.text()
            setUploadDate(element.selectFirst(".series-ep-list-date-time"))
            scanlator = PUBLISHER
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.endsWith(DUMMY_URL_SUFFIX)) {
            throw Exception("Login required to see this chapter")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageList = mutableListOf<Page>()

        // Get some essential info from document
        val viewer = document.selectFirst("#comici-viewer")!!
        val comiciViewerId = viewer.attr("comici-viewer-id")
        val memberJwt = viewer.attr("data-member-jwt")
        val requestUrl = "$baseUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", memberJwt)
            .addQueryParameter("page-from", "0")

        // Initial request to get total pages
        val initialRequest = GET(requestUrl.addQueryParameter("page-to", "1").build(), headers)
        client.newCall(initialRequest).execute().use { initialResponseRaw ->
            if (!initialResponseRaw.isSuccessful) {
                throw Exception("Failed to get page list")
            }

            // Get all pages
            val pageTo = initialResponseRaw.parseAs<PageResponse>().totalPages.toString()
            val getAllPagesUrl = requestUrl.setQueryParameter("page-to", pageTo).build()
            val getAllPagesRequest = GET(getAllPagesUrl, headers)
            client.newCall(getAllPagesRequest).execute().use {
                if (!it.isSuccessful) {
                    throw Exception("Failed to get page list")
                }

                it.parseAs<PageResponse>().result.forEach { resultItem ->
                    // Origin scramble string is something like [6, 9, 14, 15, 8, 3, 4, 12, 1, 5, 0, 7, 13, 2, 11, 10]
                    val scramble = resultItem.scramble.drop(1).dropLast(1).replace(", ", "-")
                    // Add fragment to let interceptor descramble the image
                    val imageUrl = resultItem.imageUrl.toHttpUrl().newBuilder().fragment(scramble).build()

                    pageList.add(
                        Page(index = resultItem.sort, imageUrl = imageUrl.toString()),
                    )
                }
            }
        }
        return pageList
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/search".toHttpUrl().newBuilder()
            .setQueryParameter("keyword", query)
            .setQueryParameter("page", page.toString())
            .build()
        return GET(searchUrl, headers)
    }

    override fun searchMangaNextPageSelector() = null

    override fun searchMangaSelector() = ".series-list a"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".manga-title")!!.text()
        setImageUrlFromElement(element)
    }

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesSelector() = "h2:contains(新連載) + .feature-list > .feature-item"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("h3")!!.text()
        setImageUrlFromElement(element)
    }

    // ========================================= Helper Functions =====================================

    companion object {
        private const val PUBLISHER = "BUSHIROAD WORKS"

        private val imageUrlRegex by lazy { Regex("^.*?webp") }

        private val DATE_PARSER by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT) }

        private const val DUMMY_URL_SUFFIX = "NeedLogin"

        private const val PAY_ICON = "💴 "
        private const val LOCK_ICON = "🔒 "
    }

    /**
     * Set cover image url from [element] for [SManga]
     */
    private fun SManga.setImageUrlFromElement(element: Element?) {
        if (element == null) {
            return
        }
        val match = imageUrlRegex.find(element.selectFirst("source")!!.attr("data-srcset"))
        // Add missing protocol
        if (match != null) {
            this.thumbnail_url = "https:${match.value}"
        }
    }

    /**
     * Set date_upload to [SChapter], parsing from string like "3月31日" to UNIX Epoch time.
     */
    private fun SChapter.setUploadDate(element: Element?) {
        if (element == null) {
            return
        }
        this.date_upload = DATE_PARSER.tryParse(element.attr("datetime"))
    }
}
