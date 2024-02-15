package eu.kanade.tachiyomi.extension.ja.yanmaga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class YanmagaGravures : Yanmaga("search-item-category--gravures", true) {

    override val name = "ヤンマガ（グラビア）"

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/gravures/series?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select("a.banner-link").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.attr("href"))
                title = it.selectFirst(".text-wrapper h2")!!.text()
                thumbnail_url = it.selectFirst(".img-bg-wrapper")?.absUrl("data-bg")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination > li.page-item > a.page-next") != null

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".detail-header-title")!!.text()
            genre = document.select(".ga-tag").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".detail-header-image img")?.absUrl("src")
        }
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
