package eu.kanade.tachiyomi.extension.ja.rawinu

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class RawINU : FMReader() {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::ddosChallengeInterceptor)
        .addCookie("smartlink_shown" to "1")
        .rateLimit(2) { it.host == baseUrlHost }

    private val patternDdosKey = """'([a-f0-9]{32})'""".toRegex()

    private fun ddosChallengeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 200) return response
        if (response.header("Content-Type")?.contains("text/html") != true) return response

        val responseBody = response.peekBody(Long.MAX_VALUE).string()
        if (!responseBody.contains("DDoS protection is activated for your IP")) return response
        val ddosKey = patternDdosKey.find(responseBody)?.groupValues?.get(1) ?: return response

        val cookie = Cookie.parse(request.url, "ct_anti_ddos_key=$ddosKey")
        client.cookieJar.saveFromResponse(request.url, listOfNotNull(cookie))

        // Redo exact same request
        return chain.proceed(request)
    }

    private val apiEndpoint = "$baseUrl/app/manga/controllers"

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        thumbnail_url = thumbnail_url?.removeSurrounding("'")
    }

    // =========================== Manga Details ============================
    override val infoElementSelector = "div.card-body div.row"

    // ============================== Chapters ==============================
    override suspend fun fetchChapterList(manga: SManga, mangaPage: Document): List<SChapter> {
        val slug = manga.url.substringAfter("/manga-").substringBefore(".html")
        val doc = client.get("$apiEndpoint/cont.Listchapter.php?slug=$slug").asJsoup()
        doc.setBaseUri(baseUrl) // Fixes chapter URLs
        return doc.select(chapterListSelector()).map(::chapterFromElement)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.attr(chapterNameAttrSelector).trim()
        date_upload = element.select(chapterTimeSelector).run { if (hasText()) parseRelativeDate(text()) else 0 }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val id = document.selectFirst("input[name=chapter]#chapter")!!.attr("value")

        return client.get("$apiEndpoint/cont.imagesChap.php?cid=$id").asJsoup().select(pageListImageSelector).mapIndexed { i, img ->
            Page(i, document.location(), getImgAttr(img))
        }
    }
}
