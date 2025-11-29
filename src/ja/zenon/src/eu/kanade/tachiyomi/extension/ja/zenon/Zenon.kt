package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class Zenon : GigaViewer(
    "Zenon",
    "https://comic-zenon.com/",
    "ja",
    "https://cdn-img.comic-zenon.com/public/page",
    isPaginated = true,
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "ゼノン編集部"

    override fun popularMangaSelector(): String = "ul.series-table-list li.subpage-table-list-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.title")!!.text()
        thumbnail_url = element.selectFirst("div.subpage-image-wrapper img")!!.attr("data-src")
            .replace("{width}", "528")
            .replace("{height}", "528")
        setUrlWithoutDomain(element.attr("href"))
    }

    override val chapterListMode = CHAPTER_LIST_LOCKED

    }