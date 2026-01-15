package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class BaoBua() : SimpleParsedHttpSource() {

    override val baseUrl = "https://baobua.net"
    override val lang = "all"
    override val name = "BaoBua"
    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private fun normalizeImageUrl(url: String): String {
        return if (WP_COM_REGEX.containsMatchIn(url)) {
            url.replace(WP_COM_REPLACE_REGEX, "https://")
                .replace("?w=640", "")
        } else {
            url
        }
    }

    override fun simpleMangaSelector() = ".product-item"

    override fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".product-title")!!.text()
        thumbnail_url = element.selectFirst("img.product-imgreal")?.absUrl("src")
            ?.let { normalizeImageUrl(it) }
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun simpleNextPageSelector(): String = ".pagination-custom .nextPage"

    // region popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/?page=$page", headers)
    // endregion

    // region latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    // endregion

    // region Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.isBlank()) {
            super.fetchSearchManga(page, query, filters)
        } else {
            throw UnsupportedOperationException("Full-text search is not supported")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.firstInstance<SourceCategorySelector>()
        return filter.selectedCategory?.let {
            GET(it.buildUrl(baseUrl, page), headers)
        } ?: popularMangaRequest(page)
    }

    // region Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            genre = document.select(".article-tags a").joinToString { it.text() }
            status = 2
        }
    }

    override fun chapterListSelector() = "html"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        chapter_number = 0F
        setUrlWithoutDomain(element.selectFirst("link[rel=canonical]")!!.absUrl("href"))
        date_upload = POST_DATE_FORMAT.tryParse(element.selectFirst(".article-date-comment .date")?.text())
        name = "Gallery"
    }
    // endregion

    // region Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = document.select(".article-body img")
            .mapIndexed { index, element ->
                Page(index, imageUrl = normalizeImageUrl(element.absUrl("src")))
            }

        val nextPageUrl = document.selectFirst("a.page-numbers:contains(Next)")
            ?.absUrl("href")
            ?: return pages

        val nextDoc = client.newCall(GET(nextPageUrl, headers))
            .execute()
            .asJsoup()

        return pages + pageListParse(nextDoc)
    }
    // endregion

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Searching is not supported"),
        Filter.Separator(),
        SourceCategorySelector.create(),
    )

    companion object {
        private val WP_COM_REGEX = Regex("^https://i\\d+\\.wp\\.com/")
        private val WP_COM_REPLACE_REGEX = Regex("https://i\\d+\\.wp\\.com/")
        private val POST_DATE_FORMAT = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
    }
}
