package eu.kanade.tachiyomi.extension.en.madarascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MadaraScans : MangaThemesia(
    "Madara Scans",
    "https://madarascans.com",
    "en",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US),
) {
    // support for both popular/latest tabs and search
    override fun searchMangaSelector() = "div.listupd>div, div.legend-inner"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        // support for both popular/latest tabs and search
        val titleElement = element.select("h3.card-v-title > a, h3.legend-title > a")
        title = titleElement.text()
        setUrlWithoutDomain(titleElement.attr("href"))
    }

    // manga details
    override val seriesDetailsSelector = "div.lh-container"
    override val seriesTitleSelector = "h1.lh-title"
    override val seriesDescriptionSelector = "div.lh-story > #manga-story"
    override val seriesAltNameSelector = ".fa-info-circle"
    override val seriesGenreSelector = ".lh-genres > .lh-genre-tag"
    override val seriesStatusSelector = "span.status-badge-lux"
    override val seriesThumbnailSelector = ".lh-poster > img"

    // limiting chapters to free
    override fun chapterListSelector(): String = ".ch-item.free"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".ch-num").text().ifBlank { urlElements.first()!!.text() }
        val dateElement = element.select(".ch-date")?.text()
        date_upload = dateElement.parseChapterDate()
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        if (!genrelist.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreListFilter(intl["genre_filter_title"], getGenreList()),
                ),
            )
        } else {
            filters.add(
                Filter.Header(intl["genre_missing_warning"]),
            )
        }
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header(intl["project_filter_warning"]),
                    Filter.Header(intl.format("project_filter_name", name)),
                    ProjectFilter(intl["project_filter_title"], projectFilterOptions),
                ),
            )
        }
        return FilterList(filters)
    }

    override val pageSelector = ".pagination, .legendary-pagination, .magma-pagination"
}
