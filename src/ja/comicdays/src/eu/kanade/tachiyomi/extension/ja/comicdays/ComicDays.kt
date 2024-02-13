package eu.kanade.tachiyomi.extension.ja.comicdays

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class ComicDays : GigaViewer(
    "Comic Days",
    "https://comic-days.com",
    "ja",
    "https://cdn-img.comic-days.com/public/page",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "講談社"

    override fun popularMangaSelector(): String = "ul.daily-series li.daily-series-item:has(a.link)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.daily-series-title")!!.text()
        thumbnail_url = element.selectFirst("div.daily-series-thumb img")!!
            .attr("data-src")
        setUrlWithoutDomain(element.selectFirst("a.link")!!.attr("href"))
    }

    override fun latestUpdatesSelector(): String = "section#$dayOfWeek.daily " + popularMangaSelector()

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載作品一覧", ""),
    )
}
