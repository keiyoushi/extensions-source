package eu.kanade.tachiyomi.extension.en.witchscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class WitchScans : MangaThemesia(
    "WitchScans",
    "https://witchscans.com",
    "en",
) {
    override fun chapterListSelector() = "div.eplister ul li:has(div.chbox):has(div.eph-num):has(a[href])"

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
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
        return FilterList(filters)
    }
}
