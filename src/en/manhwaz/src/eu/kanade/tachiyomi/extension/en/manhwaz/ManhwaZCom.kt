package eu.kanade.tachiyomi.extension.en.manhwaz

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class ManhwaZCom : ManhwaZ() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this.rateLimit(2)

    // The original homepage popular slider (#slide-top) was removed by
    // the site, so the inherited selector returns nothing. Site is named
    // "ManhwaZ" and its dominant catalog is manhwa (57 pages vs 23 manga,
    // 43 manhua), so reuse the manhwa genre listing sorted by views.
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/genre/manhwa".toHttpUrl().newBuilder()
            .addQueryParameter("m_orderby", "views")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseMangaPage(response, popularMangaSelector(), ::popularMangaFromElement)
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)
}
