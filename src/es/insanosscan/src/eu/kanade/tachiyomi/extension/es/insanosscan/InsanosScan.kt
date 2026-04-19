package eu.kanade.tachiyomi.extension.es.insanosscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class InsanosScan : ParsedHttpSource() {

    override val name = "InsanosScan"
    override val baseUrl = "https://insanoslibrary.com"
    override val lang = "es"
    override val supportsLatest = true

    private val nonce: String by lazy {
        val doc = client.newCall(GET(baseUrl, headers)).execute().use { it.asJsoup() }
        val b64 = doc.selectFirst("script#adar-main-js-extra")
            ?.attr("src")
            ?.removePrefix("data:text/javascript;base64,")
            ?: return@lazy ""
        val js = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
        Regex(""""nonce"\s*:\s*"([^"]+)"""").find(js)?.groupValues?.get(1) ?: ""
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/?orderby=views&page=$page", headers)

    override fun popularMangaSelector() = "article.catalog-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.catalog-card__link")!!
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("h2.catalog-card__title")!!.text()
        thumbnail_url = element.selectFirst("img.catalog-card__cover")?.attr("src")
    }

    override fun popularMangaNextPageSelector() = "div.catalog-pagination a.page-numbers.next"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/?orderby=date&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = FormBody.Builder()
            .add("action", "adar_search")
            .add("nonce", nonce)
            .add("query", query)
            .build()
        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val root = JSONObject(response.body.string())
        val data = root.optJSONArray("data") ?: return MangasPage(emptyList(), false)
        val mangas = (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)
            SManga.create().apply {
                setUrlWithoutDomain(obj.getString("url"))
                title = obj.getString("title")
                thumbnail_url = obj.optString("cover").ifEmpty { null }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.series-main-title")!!.text()
        thumbnail_url = document.selectFirst("img.series-cover-img")?.attr("src")
        description = document.selectFirst("div.synopsis-content")?.text()

        status = when (
            document.selectFirst("span.data-badge--status")?.text()?.trim()?.lowercase()
        ) {
            "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        genre = document.select("td.genres-cell a.genre-pill")
            .joinToString { it.text().trim() }
    }

    override fun chapterListSelector() = "div.chapters-list a.chapter-row"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("span.chapter-row__num")?.text()
            ?: element.selectFirst("span.chapter-row__title")?.text()
            ?: "Capítulo"
        date_upload = parseDate(element.selectFirst("span.chapter-row__date")?.text())
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching {
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("es"))
            sdf.parse(raw.trim())?.time ?: 0L
        }.getOrDefault(0L)
    }

    override fun pageListParse(document: Document): List<Page> = document.select("div.reader-pages ~ div img, div.reader-pages + div img")
        .mapIndexed { index, img ->
            Page(index, "", img.attr("src").ifEmpty { img.attr("data-src") })
        }
        .ifEmpty {
            document.select("body.reader-body img[src*='adar_manga']")
                .mapIndexed { index, img -> Page(index, "", img.attr("src")) }
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
