package eu.kanade.tachiyomi.extension.ja.comicgardo

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class ComicGardo :
    GigaViewer(
        "Comic Gardo",
        "https://comic-gardo.com",
        "ja",
        "https://cdn-img.comic-gardo.com/public/page",
        isPaginated = true,
    ) {

    override val supportsLatest: Boolean = false

    override val client = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "オーバーラップ"

    override fun popularMangaSelector(): String = "a[class^=SeriesListItem_link_]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("[class^=SeriesListItem_series_title_]")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaSelector(): String = "ul[class^=SearchResult_search_result_list_] li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("p[class^=SearchResultItem_series_title_]")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載作品", ""),
        Collection("アンソロジー・読切", "oneshot"),
        Collection("連載終了作品", "completed"),
    )
}
