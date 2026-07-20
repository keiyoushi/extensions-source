package eu.kanade.tachiyomi.extension.pt.acervohentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class AcervoHentai : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    override val mangaSubString = "manhwa"
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.post-status .summary-heading:contains(Status) + .summary-content"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2) { !it.encodedPath.startsWith("/wp-content/uploads/") }
        .build()

    // Search results are rendered with the grid layout instead of the classic one
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Only the options actually honored by the server
    override val orderByFilterOptions: Map<String, String> = mapOf(
        intl["order_by_filter_relevance"] to "",
        intl["order_by_filter_latest"] to "latest",
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_views"] to "views",
    )

    // Site ignores the genre[], author, artist, release and adult query parameters
    override fun getFilterList(): FilterList = FilterList(
        StatusFilter(
            title = intl["status_filter_title"],
            status = statusFilterOptions.map { Tag(it.key, it.value) },
        ),
        OrderByFilter(
            title = intl["order_by_filter_title"],
            options = orderByFilterOptions.toList(),
        ),
    )

    // Site lists chapters in ascending order
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}
