package eu.kanade.tachiyomi.extension.ja.yanmaga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class YanmagaGravures : Yanmaga("search-item-category--gravures", true) {

    override val name = "ヤンマガ（グラビア）"

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/gravures/series?page=$page", headers)

    override fun popularMangaSelector() = "a.banner-link"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst(".text-wrapper h2")!!.text()
        thumbnail_url = element.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination > li.page-item > a.page-next"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // Search returns gravure books instead of series
    override fun searchMangaFromElement(element: Element) = super.searchMangaFromElement(element)
        .apply {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return if (manga.url.contains("/series/")) {
            super.fetchMangaDetails(manga)
        } else {
            Observable.just(manga)
        }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".detail-header-title")!!.text()
        genre = document.select(".ga-tag").joinToString { it.text() }
        thumbnail_url = document.selectFirst(".detail-header-image img")?.absUrl("src")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.url.contains("/series/")) {
            super.fetchChapterList(manga)
        } else {
            Observable.just(
                listOf(
                    SChapter.create().apply {
                        url = manga.url
                        name = "作品"
                    },
                ),
            )
        }
    }
}
