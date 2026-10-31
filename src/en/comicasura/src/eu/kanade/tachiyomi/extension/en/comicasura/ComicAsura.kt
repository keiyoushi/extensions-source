package eu.kanade.tachiyomi.extension.en.comicasura

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ComicAsura : MangaThemesia() {
    override val datePattern = "MMMM d yyyy"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override val popularFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "rating")) }
    override val latestFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "latest")) }

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = "$baseUrl/advanced-search".toHttpUrl().newBuilder().apply {
        addQueryParameter("name", query)
        addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> addQueryParameter("status", filter.selectedValue())
                is TypeFilter -> addQueryParameter("type", filter.selectedValue().lowercase())
                is OrderByFilter -> addQueryParameter("sort", filter.selectedValue())
                is GenreListFilter -> addQueryParameter(
                    "genres",
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .joinToString("_") { it.value },
                )
                else -> {}
            }
        }
        addPathSegment("")
    }

    override fun searchMangaSelector() = ".grid > a[href*=manga], .flex-wrap.flex a[href*=manga]"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        title = element.select("img").attr("title")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = "a:has(img[alt=Next])"

    override val seriesDetailsSelector = """.bg-\[\#222222\]:has(h1)"""
    override val seriesTitleSelector = ".comic-title-content"
    override val seriesThumbnailSelector = "img[alt=poster]"
    override val seriesDescriptionSelector = ".comic-content.mobile"
    override val seriesGenreSelector = "div.hidden div:contains(Genres) + div > a"
    override val seriesStatusSelector = "div:contains(Status) + div"

    override fun chapterListSelector() = ".chapter-items"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".text-sm.text-white").text()
        date_upload = element.selectFirst(""".text-xs.text-\[\#A2A2A2\]:not(:has(span))""")?.text()
            ?.replace(DATE_SUFFIX_REGEX, "")
            .parseChapterDate()
    }

    override val pageSelector = "div > img.object-cover.mx-auto"

    override val orderByFilterOptions = arrayOf(
        Pair(intl["order_by_filter_default"], ""),
//        Pair(intl["order_by_filter_az"], "name_asc"), // The source contains an error
        Pair(intl["order_by_filter_za"], "name_desc"),
        Pair(intl["order_by_filter_latest_update"], "latest"),
        Pair(intl["order_by_filter_popular"], "rating"),
    )

    override fun parseGenres(document: Document): List<GenreData>? = document.select(".filter-dropdown-container label:has(input[name*=genres])").map { li ->
        GenreData(
            li.selectFirst("span")!!.text(),
            li.selectFirst("input[type=checkbox]")!!.attr("value"),
        )
    }

    companion object {
        val DATE_SUFFIX_REGEX = "(?<=\\d)(st|nd|rd|th)".toRegex()
    }
}
