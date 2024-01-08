package eu.kanade.tachiyomi.extension.ja.sundaywebevery

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class SundayWebEvery : GigaViewer(
    "Sunday Web Every",
    "https://www.sunday-webry.com",
    "ja",
    "https://cdn-img.www.sunday-webry.com/public/page",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "小学館"

    override fun popularMangaSelector(): String = "ul.webry-series-list li a.webry-series-item-link"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.series-title")!!.text()
        thumbnail_url = element.selectFirst("div.thumb-wrapper img")!!.attr("data-src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun latestUpdatesSelector(): String = "h3#series-$dayOfWeek + section " + popularMangaSelector()

    override val chapterListMode = CHAPTER_LIST_LOCKED

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載作品", ""),
        Collection("読切", "oneshot"),
        Collection("夜サンデー", "yoru-sunday"),
    )
}
