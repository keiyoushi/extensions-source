package eu.kanade.tachiyomi.extension.all.misskon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MissKon() : SimpleParsedHttpSource() {

    override val baseUrl = "https://misskon.com"
    override val lang = "all"
    override val name = "MissKon"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .build()

    override fun simpleMangaSelector() = "article.item-list"

    override fun simpleMangaFromElement(element: Element): SManga {
        val titleEL = element.selectFirst(".post-box-title")!!
        return SManga.create().apply {
            title = titleEL.text()
            thumbnail_url = element.selectFirst(".post-thumbnail img")?.absUrl("data-src")
            setUrlWithoutDomain(titleEL.selectFirst("a")!!.absUrl("href"))
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun simpleNextPageSelector(): String? = null

    // region popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top3/", headers)
    // endregion

    // region latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesNextPageSelector() = ".current + a.page"
    // endregion

    // region Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.firstInstance<SourceCategorySelector>()
        return filter.selectedCategory?.let {
            GET("$baseUrl${it.url}", headers)
        } ?: run {
            "$baseUrl/page/$page/".toHttpUrl().newBuilder()
                .addEncodedQueryParameter("s", query)
                .build()
                .let { GET(it, headers) }
        }
    }

    override fun searchMangaNextPageSelector() = "div.content > div.pagination > span.current + a"
    // endregion

    // region Details
    override fun mangaDetailsParse(document: Document): SManga {
        val postInnerEl = document.selectFirst("article > .post-inner")!!
        return SManga.create().apply {
            title = postInnerEl.select(".post-title").text()
            genre = postInnerEl.select(".post-tag > a").joinToString { it.text() }
        }
    }

    override fun chapterListSelector() = "html"

    override fun chapterFromElement(element: Element): SChapter {
        val dateStr = element.selectFirst(".entry img")?.absUrl("data-src")
            ?.let { url ->
                FULL_DATE_REGEX.find(url)?.groupValues?.get(1)
                    ?: YEAR_MONTH_REGEX.find(url)?.groupValues?.get(1)?.let { "$it/01" }
            }

        return SChapter.create().apply {
            chapter_number = 0F
            setUrlWithoutDomain(element.selectFirst("link[rel=canonical]")!!.absUrl("href"))
            name = "Gallery"
            date_upload = FULL_DATE_FORMAT.tryParse(dateStr)
        }
    }
    // endregion

    // region Pages
    override fun pageListParse(document: Document): List<Page> {
        val basePageUrl = document.selectFirst("link[rel=canonical]")!!.absUrl("href")

        val pages = mutableListOf<Page>()
        document.select("div.post-inner div.page-link:nth-child(1) .post-page-numbers")
            .forEachIndexed { index, pageEl ->
                val doc = when (index) {
                    0 -> document
                    else -> {
                        val url = "$basePageUrl${pageEl.text()}/"
                        client.newCall(GET(url, headers)).execute().asJsoup()
                    }
                }
                doc.select("div.post-inner > div.entry > p > img")
                    .map { it.absUrl("data-src") }
                    .forEach { pages.add(Page(pages.size, imageUrl = it)) }
            }
        return pages
    }
    // endregion

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Unable to further search in the category!"),
        Filter.Separator(),
        SourceCategorySelector.create(),
    )

    companion object {

        private val FULL_DATE_REGEX = Regex("""/(\d{4}/\d{2}/\d{2})/""")
        private val YEAR_MONTH_REGEX = Regex("""/(\d{4}/\d{2})/""")

        private val FULL_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
