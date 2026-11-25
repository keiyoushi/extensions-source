package eu.kanade.tachiyomi.extension.ja.raw18

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Raw18 : WPComics() {

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.JAPANESE)

    override val gmtOffset = null
    override val searchPath = "search/manga"
    override val mangaUrlDirectory = "manga"

    override val genresUrlDelimiter = "="

    override fun popularMangaSelector() = "div.items .item"

    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li a.page-link"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        description = document.selectFirst("div.detail-content")?.text()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/manga".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addQueryParameter("genre", it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    else -> {}
                }
            }
            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaPage(client.get(url, Fetch.DOCUMENT), searchMangaSelector(), ::searchMangaFromElement)
    }

    override fun getStatusList(): List<Pair<String?, String>> = listOf(
        Pair(null, intl["STATUS_ALL"]),
        Pair("ongoing", intl["STATUS_ONGOING"]),
    )
}
