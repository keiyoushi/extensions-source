package eu.kanade.tachiyomi.extension.en.hentai18

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Ignored with a text search"),
    ListFilter(),
    SortFilter(),
    Filter.Separator(),
    Filter.Header("A tag replaces the list, e.g. big-breasts"),
    TagFilter(),
)

class ListFilter : Filter.Select<String>("List", LISTS.map { it.first }.toTypedArray(), 1) {
    val path get() = LISTS[state].second
}

class SortFilter : Filter.Select<String>("Sort (categories and tags)", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class TagFilter : Filter.Text("Tag")

private val LISTS = listOf(
    "New" to "new-manga-hentai",
    "Top" to "top-hentai",
    "Manhwa" to "category/manhwa",
    "Manga" to "category/manga",
    "Doujinshi" to "category/doujinshi",
    "Porn Comics" to "category/porn-comics",
    "English manhwa" to "manhwa-english",
    "Raw manhwa" to "manhwa-raw",
    "Completed" to "completed-manhwa-hentai",
)

private val SORTS = listOf(
    "Default" to null,
    "New" to "new",
    "Popular" to "popular",
    "Rating" to "rating",
)
