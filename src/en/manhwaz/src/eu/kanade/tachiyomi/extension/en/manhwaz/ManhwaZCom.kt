package eu.kanade.tachiyomi.extension.en.manhwaz

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element

class ManhwaZCom :
    ManhwaZ(
        "ManhwaZ",
        "https://manhwaz.com",
        "en",
    ) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // The original homepage popular slider (#slide-top) was removed by
    // the site, so the inherited selector returns nothing. Site is named
    // "ManhwaZ" and its dominant catalog is manhwa (57 pages vs 23 manga,
    // 43 manhua), so reuse the manhwa genre listing sorted by views.
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/genre/manhwa".toHttpUrl().newBuilder()
            .addQueryParameter("m_orderby", "views")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
}
