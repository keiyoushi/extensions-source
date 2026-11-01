package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
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

    override fun Headers.Builder.configureHeaders() = set("Sec-GPC", "1")
        .set("Pragma", "no-cache")

    override val mangaSubString = "bl"
    override val genreDirectory = "genero"

    override val mangaDetailsSelectorTitle = ".xyaoi-main-title, h1"
    override val mangaDetailsSelectorAuthor = "a[href*=author]"
    override val mangaDetailsSelectorArtist = "a[href*=artist]"
    override val mangaDetailsSelectorStatus = "span:contains(status) + span"
    override val mangaDetailsSelectorDescription = "[class*=synopsis]"
    override val mangaDetailsSelectorGenre = ".xyaoi-genres-list a"
    override val mangaDetailsSelectorTag = ".wp-manga-tags-list a"
    override val seriesTypeSelector = "span:contains(tipo) + span:not(:contains(Não informado))"

    override fun searchCardSelector() = ".xyaoi-search-card"

    override val archiveUrlSelector = "h3 > a"

    override fun chapterFromElement(element: Element, mangaPath: String) = super.chapterFromElement(element, mangaPath)?.apply {
        name = element.selectFirst("div > span:nth-child(1)")!!.text()
        date_upload = parseChapterDate(element.selectFirst("div:has(> span:nth-child(1)) + div")?.text())
    }

    override suspend fun fetchChapterDocument(chapterUrl: String): Document {
        val html = client.get(chapterUrl, Fetch.DOCUMENT).body.string()
        val rendered = try {
            runWebView<String> {
                userAgent = headers["User-Agent"].orEmpty()
                // A script load error makes the site's ad check delete the decoded pages
                interceptRequest { request ->
                    if (request.isForMainFrame) null else WebResourceResponse("text/javascript", null, 200, "OK", null, ByteArrayInputStream(ByteArray(0)))
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
