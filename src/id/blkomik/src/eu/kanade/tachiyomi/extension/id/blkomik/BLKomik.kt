package eu.kanade.tachiyomi.extension.id.blkomik

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZonedDateTime

@Source
abstract class BLKomik : WPComics() {

    override val searchPath = "search/manga"
    override val mangaUrlDirectory = "manga"

    override val genresUrlDelimiter = "="

    override fun popularMangaSelector() = "div.items .item"

    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li a.page-link"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        author = author?.takeUnless { it == PLACEHOLDER }
        description = MarkdownDescription.Builder().apply {
            document.selectFirst("div.detail-content")?.select("p")?.forEach { description(it) }
            field(intl["OTHER_NAME"], document.selectFirst("li.othername p.col-xs-8")?.text()?.takeUnless { it == PLACEHOLDER })
        }.build()
    }

    // Chapter dates are only relative Indonesian text, e.g. "21 jamyang lalu"
    override fun String?.toDate(): Long {
        val date = this ?: return 0L
        val amount = NUMBER_REGEX.find(date)?.value?.toLong() ?: return 0L
        val now = ZonedDateTime.now(dateZone)
        return when {
            "detik" in date -> now.minusSeconds(amount)
            "menit" in date -> now.minusMinutes(amount)
            "jam" in date -> now.minusHours(amount)
            "hari" in date -> now.minusDays(amount)
            "minggu" in date -> now.minusWeeks(amount)
            "bulan" in date -> now.minusMonths(amount)
            "tahun" in date -> now.minusYears(amount)
            else -> return 0L
        }.toInstant().toEpochMilli()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addQueryParameter("genre", it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    is SortFilter -> filter.toUriPart()?.let { addQueryParameter("sort", it) }
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
        Pair("0", intl["STATUS_ONGOING"]),
        Pair("1", intl["STATUS_COMPLETED"]),
    )

    override fun getSortList(): List<Pair<String?, String>> = listOf(
        Pair(null, intl["SORT_UPDATED"]),
        Pair("15", intl["SORT_NEW"]),
        Pair("10", intl["SORT_TOP_ALL"]),
        Pair("11", intl["SORT_TOP_MONTH"]),
        Pair("12", intl["SORT_TOP_WEEK"]),
        Pair("13", intl["SORT_TOP_DAY"]),
        Pair("20", intl["SORT_FOLLOW"]),
        Pair("30", intl["SORT_CHAPTER"]),
    )

    companion object {
        private const val PLACEHOLDER = "Sedang diperbarui"
        private val NUMBER_REGEX = Regex("""\d+""")
    }
}
