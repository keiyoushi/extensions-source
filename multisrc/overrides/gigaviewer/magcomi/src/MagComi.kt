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

    override fun popularMangaSelector(): String = "ul.magcomi-series-list li.series-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3.series-title").text()
        thumbnail_url = element.select("div.series-thumb img").attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載・読切", ""),
    )
}
