package eu.kanade.tachiyomi.extension.ja.magazinepocket

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class MagazinePocket : GigaViewer(
    "Magazine Pocket",
    "https://pocket.shonenmagazine.com",
    "ja",
    "https://cdn-img.pocket.shonenmagazine.com/public/page",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "講談社"

    override fun popularMangaSelector(): String = "ul.daily-series li.daily-series-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.daily-series-title")!!.text()
        thumbnail_url = element.selectFirst("div.daily-series-thumb img")!!.attr("data-src")
        setUrlWithoutDomain(element.attr("href")!!)
    }

    override fun latestUpdatesSelector(): String = "section.daily.$dayOfWeek " + popularMangaSelector()

    override fun getCollections(): List<Collection> = listOf(
        Collection("マガポケ連載一覧", ""),
        Collection("週刊少年マガジン連載一覧", "smaga"),
        Collection("別冊少年マガジン連載一覧", "bmaga"),
    )
}
