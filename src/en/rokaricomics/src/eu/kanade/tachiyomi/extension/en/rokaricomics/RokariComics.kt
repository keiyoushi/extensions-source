package eu.kanade.tachiyomi.extension.en.rokaricomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class RokariComics : MangaThemesia(
    "RokariComics",
    "https://rokaricomics.com",
    "en",
) {
    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Separator(),
            StatusFilter(intl["status_filter_title"], statusOptions),
            TypeFilter(intl["type_filter_title"], typeFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
            Filter.Header(intl["genre_exclusion_warning"]),
            GenreListFilter(intl["genre_filter_title"], getGenreList()),
        )
    }
}
