package eu.kanade.tachiyomi.extension.ja.kuragebunch

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element

class KurageBunch : GigaViewer(
    "Kurage Bunch",
    "https://kuragebunch.com",
    "ja",
    "https://cdn-img.kuragebunch.com",
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "株式会社"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/kuragebunch", headers)

    override fun popularMangaSelector(): String = "ul.page-series-list li div.item-box"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("a.series-data-container h4")!!.text()
        thumbnail_url = element.selectFirst("a.series-thumb img")!!.attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun chapterListSelector(): String = "li.episode"

    override fun getCollections(): List<Collection> = listOf(
        Collection("くらげバンチ", "kuragebunch"),
        Collection("読切", "oneshot"),
        Collection("月刊コミックバンチ", "comicbunch"),
        Collection("Bバンチ", "bbunch"),
        Collection("ututu", "ututu"),
    )
}
