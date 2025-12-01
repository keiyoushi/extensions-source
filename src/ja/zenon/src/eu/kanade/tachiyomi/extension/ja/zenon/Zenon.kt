package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
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
    true,
) {

    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "ゼノン編集部"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.kyujosho-series > a"

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

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "ul.panels li.panel a:has(h4)"

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangasPage = super.latestUpdatesParse(response)
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
