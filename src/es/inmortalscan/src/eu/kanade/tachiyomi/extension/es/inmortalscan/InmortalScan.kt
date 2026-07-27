package eu.kanade.tachiyomi.extension.es.inmortalscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class InmortalScan : Madara() {
    override val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("es"))

    override val mangaSubString = "mg"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override fun popularMangaSelector() = "article.scanim-catalog-card"

    override val popularMangaUrlSelector = "h3 a"
    override val popularMangaUrlSelectorImg = "a.scanim-catalog-card__cover img"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/?catalog_page=$page&catalog_order=rating", headers)

    override fun popularMangaNextPageSelector() = "a:contains(Siguiente)"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/?catalog_page=$page&catalog_order=updated", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/$mangaSubString/?catalog_page=$page&catalog_search=$query", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override val searchMangaUrlSelector = popularMangaUrlSelector

    // Manga Details Selector
    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorStatus = "span.scanim-series-status"
    override val mangaDetailsSelectorDescription = "div.scanim-series-description p:not(.scanim-seo-info p)"
    override val mangaDetailsSelectorThumbnail = "div.scanim-series-cover img"
    override val mangaDetailsSelectorGenre = "a[href*='manga-genre']"
    override val altNameSelector = "p.scanim-series-alternative"
}
