package eu.kanade.tachiyomi.extension.ja.magcomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class MagComi : GigaViewer(
    "MAGCOMI",
    "https://magcomi.com",
    "ja",
    "https://cdn-img.magcomi.com/public/page",
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "マッグガーデン"

    override fun popularMangaSelector(): String = "ul[class^=\"SeriesSection_series_list\"] > li > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3").text()
        thumbnail_url = element.select("div.jsx-series-thumb > span > noscript > img").attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載・読切", ""),
    )
}
