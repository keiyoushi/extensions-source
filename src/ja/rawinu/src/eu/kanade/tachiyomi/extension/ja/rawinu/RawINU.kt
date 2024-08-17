package eu.kanade.tachiyomi.extension.ja.rawinu

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RawINU : FMReader(
    "RawINU",
    "https://rawinu.com",
    "ja",
) {
    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .addInterceptor(::ddosChallengeInterceptor)
        .build()

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

    // =========================== Manga Details ============================
    override val infoElementSelector = "div.card-body div.row"

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/manga-").substringBefore(".html")
        return GET("$apiEndpoint/cont.Listchapter.php?slug=$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        doc.setBaseUri(baseUrl) // Fixes chapter URLs
        return doc.select(chapterListSelector()).map(::chapterFromElement)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.attr(chapterNameAttrSelector).trim()
        date_upload = element.select(chapterTimeSelector).run { if (hasText()) parseRelativeDate(text()) else 0 }
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val id = document.selectFirst("input[name=chapter]#chapter")!!.attr("value")
        val req = client.newCall(GET("$apiEndpoint/cont.imagesChap.php?cid=$id", headers)).execute()

        return req.asJsoup().select(pageListImageSelector).mapIndexed { i, img ->
            Page(i, document.location(), getImgAttr(img))
        }
    }
}
