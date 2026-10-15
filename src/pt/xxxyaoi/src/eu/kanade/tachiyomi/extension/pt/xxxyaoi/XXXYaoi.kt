package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class XXXYaoi : MadaraNoAjax() {

    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override fun Headers.Builder.configureHeaders() = set("Upgrade-Insecure-Requests", "1")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Sec-GPC", "1")
        .set("Sec-Fetch-User", "?1")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Dest", "document")
        .set("Priority", "u=0, i")
        .set("Pragma", "no-cache")

    override val mangaSubString = "bl"

    override val mangaDetailsSelectorTitle = ".xyaoi-main-title, h1"
    override val mangaDetailsSelectorAuthor = "a[href*=author]"
    override val mangaDetailsSelectorArtist = "a[href*=artist]"
    override val mangaDetailsSelectorStatus = "span:contains(status) + span"
    override val mangaDetailsSelectorDescription = "[class*=synopsis]"

    override fun searchCardSelector() = ".xyaoi-search-card"

    override val archiveUrlSelector = "h3 > a"

    override fun chapterFromElement(element: Element, mangaPath: String) = super.chapterFromElement(element, mangaPath)?.apply {
        name = element.selectFirst("div > span:nth-child(1)")!!.text()
        date_upload = parseChapterDate(element.selectFirst("div:has(> span:nth-child(1)) + div")?.text())
    }

    override suspend fun fetchChapterDocument(chapterUrl: String): Document {
        val html = client.get(chapterUrl).body.string()
        val rendered = try {
            runWebView<String> {
                userAgent = headers["User-Agent"].orEmpty()
                interceptRequest { request ->
                    if (request.isForMainFrame) null else WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
                }
                onPageFinished {
                    evaluateJs("document.documentElement.outerHTML") { resolve(it.parseAs<String>()) }
                }
                loadData(chapterUrl, html)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        return rendered.asJsoup(chapterUrl)
    }
}
