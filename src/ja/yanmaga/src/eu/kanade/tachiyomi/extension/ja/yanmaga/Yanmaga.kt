package eu.kanade.tachiyomi.extension.ja.yanmaga

import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbInterceptor
import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Yanmaga(
    private val searchCategoryClass: String,
    private val highQualityImages: Boolean = false,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT),
) : ParsedHttpSource() {

    override val baseUrl = "https://yanmaga.jp"

    override val lang = "ja"

    protected val json = Injekt.get<Json>()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", query)
            addQueryParameter("kind", "human")

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            addQueryParameter("search-submit", "")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "ul.search-list > li.search-item:has(.$searchCategoryClass)"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".search-item-title")!!.text()
        thumbnail_url = element.selectFirst(".search-item-thumbnail-image img")?.absUrl("src")
    }

    override fun searchMangaNextPageSelector() = "ul.pagination > li.page-item > a.page-next"

    // Longer chapter lists are fetched through AJAX, the response being a JavaScript script
    // that inserts raw HTML into the DOM. Horror.
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        if (document.selectFirst(".js-episode") == null) {
            return document.select(chapterListSelector())
                .map { chapterFromElement(it) }
                .filter { it.url.isNotEmpty() }
        }

        val chapterUrl = response.request.url.toString()
        val firstChapterList = document
            .select("ul.mod-episode-list:first-of-type > li.mod-episode-item")
            .map { chapterFromElement(it) }
        val lastChapterList = document
            .select("ul.mod-episode-list:last-of-type > li.mod-episode-item")
            .map { chapterFromElement(it) }
        val totalChapterCount = document
            .selectFirst("#contents")
            ?.attr("data-count")
            ?.toInt()
            ?: return firstChapterList + lastChapterList
        val chapterMoreButton = document.selectFirst(".mod-episode-more-button[data-offset][data-path]")
            ?: return firstChapterList + lastChapterList
        val chapterOffset = chapterMoreButton.attr("data-offset").toInt()
        val chapterAjaxUrl = chapterMoreButton.attr("abs:data-path").toHttpUrl()
        val chaptersPerPage = document
            .selectFirst("script:containsData(gon.episode_more)")
            ?.data()
            ?.substringAfter("gon.episode_more=")
            ?.substringBefore(";")
            ?.toInt()
            ?: 150
        val headers = headers.newBuilder()
            .set("Referer", chapterUrl)
            .set("X-CSRF-Token", document.selectFirst("meta[name=csrf-token]")!!.attr("content"))
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return buildList(totalChapterCount) {
            addAll(firstChapterList)

            for (i in chapterOffset until totalChapterCount - lastChapterList.size step chaptersPerPage) {
                val limit = totalChapterCount - lastChapterList.size - i
                val url = chapterAjaxUrl.newBuilder().apply {
                    addQueryParameter("offset", i.toString())

                    if (limit < 150) {
                        addQueryParameter("limit", limit.toString())
                    }

                    addQueryParameter("cb", System.currentTimeMillis().toString())
                }.build()
                val script = client.newCall(GET(url, headers)).execute().body.string()

                parseInsertAdjacentHtmlScript(script)
                    .map { chapterFromElement(Jsoup.parseBodyFragment(it, chapterUrl)) }
                    .let { addAll(it) }
            }

            addAll(lastChapterList)
        }
            .filter { it.url.isNotEmpty() }
    }

    override fun chapterListSelector() = "ul.mod-episode-list > li.mod-episode-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        // The first chapter sometimes is a fake one. However, this still count towards the total
        // chapter count, so we can't filter this out yet.
        url = ""
        element.selectFirst("a.mod-episode-link")?.attr("href")?.let {
            setUrlWithoutDomain(it)
        }
        name = element.selectFirst(".mod-episode-title")!!.text()
        date_upload = try {
            dateFormat.parse(element.selectFirst(".mod-episode-date")!!.text())!!.time
        } catch (_: Exception) {
            0L
        }
    }

    private val reader by lazy { SpeedBinbReader(client, headers, json, highQualityImages) }

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst(".ga-rental-modal-sign-up") != null) {
            // Please log in with WebView to read this story
            throw Exception("このストーリーを読むには WebView でログイン")
        }

        if (document.selectFirst(".ga-modal-open") != null) {
            // Rent this story with points in WebView
            throw Exception("WebView でポイントを使用してこのストーリーをレンタル")
        }

        return reader.pageListParse(document)
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
