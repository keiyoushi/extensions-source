package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Zenon : GigaViewer(
    "Zenon",
    "https://comic-zenon.com",
    "ja",
    "https://cdn-img.comic-zenon.com/public/page",
    isPaginated = false,
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "ゼノン編集部"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.kyujosho-series > a, ul.panels li.panel a:has(h4)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4, p")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
        setUrlWithoutDomain(getCanonicalUrl(element.absUrl("href"), thumbnail_url))
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangasPage = super.popularMangaParse(response)
        val distinctMangas = mangasPage.mangas.distinctBy { it.url }
        return MangasPage(distinctMangas, mangasPage.hasNextPage)
    }

    override fun searchMangaSelector(): String = "ul.series-list > li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".series-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    private fun getCanonicalUrl(href: String, thumbnailUrl: String?): String {
        return thumbnailUrl?.let { Regex("""series-thumbnail/(\d+)""").find(it) }
            ?.let { "/series/${it.groupValues[1]}" }
            ?: href
    }

    override fun getFilterList(): FilterList = FilterList()
}
