package eu.kanade.tachiyomi.extension.ja.comicgardo

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class ComicGardo : GigaViewer(
    "Comic Gardo",
    "https://comic-gardo.com",
    "ja",
    "https://cdn-img.comic-gardo.com/public/page",
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "オーバーラップ"

    override fun popularMangaSelector(): String = "ul.series-section-list li.series-section-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h5.series-title")!!.text()
        thumbnail_url = element.selectFirst("div.thumb img")!!.attr("data-src")
        setUrlWithoutDomain(element.attr("href")!!)
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載一覧", ""),
    )
}
