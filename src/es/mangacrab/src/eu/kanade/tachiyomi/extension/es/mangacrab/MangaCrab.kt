package eu.kanade.tachiyomi.extension.es.mangacrab

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InterruptedIOException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaCrab :
    Madara(
        "Manga Crab",
        "https://mangacrab.org",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale("es")),
    ),
    ConfigurableSource {

    override val client = super.client.newBuilder()
        .rateLimit(5, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override val mangaSubString = "series"
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = ".mv-rank-panel[data-panel=monthly] .mv-rank-item"
    override fun searchMangaSelector() = ".catalog-card, .mv-recent-card, .manga-row, .manga__item"
    override fun latestUpdatesSelector() = ".manga-row"

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page/", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("page")
            .addPathSegment(page.toString())
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector() = "a.next.page-numbers, .mv-page-link a.next"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst(".mv-rank-title")?.text() ?: link.text()
        element.selectFirst("img")?.let {
            thumbnail_url = imageFromElement(it)
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.manga-row-cover")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst("h5")?.text() ?: link.text()
        element.selectFirst("img")?.let {
            thumbnail_url = imageFromElement(it)
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.mv-recent-link, a.manga-row-cover, a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst("strong.mv-recent-name, h5, h2")?.text() ?: link.text()
        element.selectFirst("img")?.let {
            thumbnail_url = imageFromElement(it)
        }
    }

    override fun chapterListSelector() = "article.chapter-item > div > a, #mv-chapter-list a"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }.distinctBy { it.url }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val htmlStr = document.outerHtml()

        val mangaId = document.selectFirst("#mv-chapter-list[data-manga-id]")?.attr("data-manga-id")
            ?: MANGA_ID_REGEX.find(htmlStr)?.groupValues?.get(1)

        if (mangaId == null) {
            return document.select(chapterListSelector()).map { chapterFromElement(it) }
        }

        val nonce = NONCE_MVTHEME_REGEX.find(htmlStr)?.groupValues?.get(1)
            ?: NONCE_FALLBACK_REGEX.find(htmlStr)?.groupValues?.get(1)
        if (nonce == null) {
            return document.select(chapterListSelector()).map { chapterFromElement(it) }
        }

        var page = 1
        var hasMore = true
        while (hasMore) {
            val form = FormBody.Builder()
                .add("action", "mv_get_chapters")
                .add("nonce", nonce)
                .add("manga_id", mangaId)
                .add("page", page.toString())
                .add("search", "")
                .add("_ts", System.currentTimeMillis().toString())
                .build()

            val request = POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
            try {
                client.newCall(request).execute().use { res ->
                    val data = res.parseAs<MvChaptersDto>()

                    if (data.isSuccess) {
                        val listHtml = data.data?.list ?: ""
                        val listDoc = Jsoup.parseBodyFragment(listHtml, baseUrl)
                        val elements = listDoc.select(chapterListSelector())
                        if (elements.isEmpty()) {
                            hasMore = false
                        } else {
                            val newChapters = elements.map { chapterFromElement(it) }
                            val existingUrls = chapters.mapTo(HashSet()) { it.url }
                            val filtered = newChapters.filterNot { it.url in existingUrls }

                            if (filtered.isEmpty()) {
                                hasMore = false
                            } else {
                                chapters.addAll(filtered)
                                page++
                            }
                        }
                    } else {
                        hasMore = false
                    }
                }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (_: Exception) {
                hasMore = false
            }
        }

        if (chapters.isEmpty()) {
            return document.select(chapterListSelector()).map { chapterFromElement(it) }
        }
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)
        if (element.tagName() == "a") {
            chapter.url = element.attr("href").substringAfter(baseUrl)
            chapter.name = element.text()
        }
        return chapter
    }

    override val mangaDetailsSelectorTitle = "h1.mb-2, h1.post-title, .post-title h1"
    override val mangaDetailsSelectorDescription = "div.mv-synopsis, div.c-page__content div.modal-contenido"

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }

    override val pageListParseSelector = "img.mv-secure-img, div.page-break:not([style*='display:none']) img:not([src]), div.reader-body img, #mv-reader-body img"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val htmlStr = document.outerHtml()

        // Extract security token for image requests
        val imgHeader = IMG_HEADER_REGEX.find(htmlStr)?.groupValues?.get(1).orEmpty()

        val pages = mutableListOf<Page>()
        document.select(pageListParseSelector).forEachIndexed { i, img: Element ->
            val rawUrl = imageFromElement(img)
            if (!rawUrl.isNullOrEmpty()) {
                val finalUrl = if (imgHeader.isNotEmpty()) "$rawUrl#nodeHeader=$imgHeader" else rawUrl
                pages.add(Page(i, imageUrl = finalUrl))
            }
        }
        return pages
    }

    // Inject the "Node" security header required by the image CDN
    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!
        if (url.contains("#nodeHeader=")) {
            val pureUrl = url.substringBefore("#nodeHeader=")
            val nodeHeader = url.substringAfter("#nodeHeader=")
            return GET(pureUrl, headersBuilder().add("Node", nodeHeader).build())
        }
        return super.imageRequest(page)
    }

    override fun imageFromElement(element: Element): String? {
        val url = element.attributes()
            .firstNotNullOfOrNull { attr ->
                element.absUrl(attr.key).toHttpUrlOrNull()
                    ?.takeIf { it.encodedQuery.toString().contains("wp-content") }
            }

        return when {
            element.hasAttr("data-sec-src") -> element.attr("abs:data-sec-src")
            url != null -> url.toString()
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ").trim()
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            element.hasAttr("data-src-base64") -> element.attr("abs:data-src-base64")
            else -> element.attr("abs:src")
        }
    }

    companion object {
        private val MANGA_ID_REGEX = Regex(""""manga_id"\s*:\s*"?(\d+)""")
        private val NONCE_MVTHEME_REGEX = Regex("""var\s+mvTheme\s*=\s*\{[^}]*"nonce"\s*:\s*"([^"]+)""")
        private val NONCE_FALLBACK_REGEX = Regex(""""nonce"\s*:\s*"([^"]+)""")
        private val IMG_HEADER_REGEX = Regex(""""imgHeader"\s*:\s*"([^"]+)""")
    }
}
