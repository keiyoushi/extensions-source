package eu.kanade.tachiyomi.extension.ar.goonscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class GoonScans : MangaThemesia() {
    override val popularFilter by lazy { FilterList(OrderByFilter("top_rated", emptyArray())) }
    override val latestFilter by lazy { FilterList(OrderByFilter("latest_chapter", emptyArray())) }

    override val datePattern = "yyyy.dd.MM"

    override fun searchMangaNextPageSelector() = ".next-btn"

    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = if (query.isEmpty()) {
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("title")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            addQueryParameter(
                "orderby",
                filters.filterIsInstance<OrderByFilter>().first().name,
            )
        }
    } else {
        super.searchMangaUrl(page, query)
    }

    override fun searchMangaSelector() = ".cover-wrapper"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val img = element.select("img")
        thumbnail_url = img.imgAttr()
        title = img.attr("alt")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override val seriesDetailsSelector = ".webtoon-container"
    override val seriesTitleSelector = ".webtoon-title"
    override val seriesThumbnailSelector = ".webtoon-cover"
    override val seriesDescriptionSelector = ".description-content"
    override val seriesGenreSelector = ".genre-tags a"
    override val seriesStatusSelector = ".cover-status-badge"

    override fun chapterListSelector() = "ul.chapter-list li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        name = element.selectFirst(".chapter-number")!!.text()
        date_upload = element.selectFirst(".chapter-date")?.text().parseChapterDate()
    }

    override val supportsFilterFetching = false
    override fun getFilterList(data: JsonElement?) = FilterList()
}
