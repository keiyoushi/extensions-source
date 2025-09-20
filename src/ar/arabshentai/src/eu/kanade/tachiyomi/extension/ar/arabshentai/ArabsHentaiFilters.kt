package eu.kanade.tachiyomi.extension.ar.arabshentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ArabsHentai : ParsedHttpSource() {
    override val name = "هنتاي العرب"

    override val baseUrl = "https://arabshentai.com"

    override val lang = "ar"

    private val dateFormat = SimpleDateFormat("d MMM\u060c yyy", Locale("ar"))

    override val supportsLatest = true

    override val client =
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .build()

    override fun headersBuilder() =
        super.headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page/?orderby=new-manga", headers)

    override fun popularMangaSelector() = "#archive-content .wp-manga"

    override fun popularMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".data h3 a")!!.run {
                setUrlWithoutDomain(absUrl("href"))
                title = text()
            }
            thumbnail_url = element.selectFirst("a .poster img")?.imgAttr()
        }

