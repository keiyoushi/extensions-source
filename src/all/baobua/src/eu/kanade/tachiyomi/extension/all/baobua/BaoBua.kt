package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class BaoBua : HttpSource() {

    override val name = "BaoBua"
    override val baseUrl = "https://baobua.net"
    override val lang = "all"
    override val supportsLatest = false
    override val disableRelatedMangas = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseMangasPage(document)
    }

    // ========================= Latest  =========================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search  =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseUrl.toHttpUrlOrNull()?.host) {
                return GET(query, headers)
            }
            throw Exception("Full-text search is not supported")
        }

        val filter = filters.firstInstance<SourceCategorySelector>()
        return filter.selectedCategory?.let {
            GET(it.buildUrl(baseUrl, page), headers)
        } ?: popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.selectFirst(".product-item") == null && document.selectFirst(".article-body") != null) {
            val manga = mangaDetailsParse(document).apply {
                val urlObj = response.request.url
                url = urlObj.encodedPath
                title = (document.selectFirst(".product-title") ?: document.selectFirst("h1") ?: document.selectFirst(".article-title") ?: document.selectFirst(".post-title"))?.text()?.trim()
                    ?: throw Exception("Title is mandatory")
                thumbnail_url = (document.selectFirst("img.product-imgreal") ?: document.selectFirst(".article-body img"))?.absUrl("src")
                    ?.let { normalizeImageUrl(it) }
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
            return MangasPage(listOf(manga), false)
        }

        return parseMangasPage(document)
    }

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        genre = document.select(".article-tags a").joinToString { it.text() }
        status = SManga.COMPLETED
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ========================= Chapters=========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                chapter_number = 0F
                val absUrl = document.selectFirst("link[rel=canonical]")?.absUrl("href")
                    ?: response.request.url.toString()
                url = absUrl.toHttpUrlOrNull()?.encodedPath ?: absUrl
                date_upload = POST_DATE_FORMAT.tryParse(document.selectFirst(".article-date-comment .date")?.text())
                name = "Gallery"
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ========================= Pages   =========================
    override fun pageListParse(response: Response): List<Page> = recursivePageListParse(response.asJsoup())

    private fun recursivePageListParse(document: Document): List<Page> {
        val pages = document.select(".article-body img")
            .mapIndexed { index, element ->
                Page(index, imageUrl = normalizeImageUrl(element.absUrl("src")))
            }

        val nextPageUrl = document.selectFirst("a.page-numbers:contains(Next)")
            ?.absUrl("href")
            ?: return pages

        val nextDoc = client.newCall(GET(nextPageUrl, headers))
            .execute().use { it.asJsoup() }

        val nextPages = recursivePageListParse(nextDoc)
        val offset = pages.size
        val redirectedNextPages = nextPages.map { Page(it.index + offset, it.url, it.imageUrl) }
        return pages + redirectedNextPages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Filters =========================
    override fun getFilterList(): FilterList = FilterList(
        SourceCategorySelector.create(),
    )

    // ========================= Helpers =========================
    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".product-item").mapNotNull { element ->
            SManga.create().apply {
                val absUrl = element.selectFirst("a")?.absUrl("href") ?: return@mapNotNull null
                url = absUrl.toHttpUrlOrNull()?.encodedPath ?: absUrl
                title = element.selectFirst(".product-title")?.text() ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img.product-imgreal")?.absUrl("src")
                    ?.let { normalizeImageUrl(it) }
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }

        val hasNextPage = document.selectFirst(".pagination-custom .nextPage") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun normalizeImageUrl(url: String): String = if (WP_COM_REGEX.containsMatchIn(url)) {
        url.replace(WP_COM_REPLACE_REGEX, "https://")
            .replace("?w=640", "")
    } else {
        url
    }

    companion object {
        private val WP_COM_REGEX = Regex("""^https://i\d+\.wp\.com/""")
        private val WP_COM_REPLACE_REGEX = Regex("""https://i\d+\.wp\.com/""")
        private val POST_DATE_FORMAT = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
    }
}
