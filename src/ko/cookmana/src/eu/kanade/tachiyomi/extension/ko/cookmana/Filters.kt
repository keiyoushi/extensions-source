package eu.kanade.tachiyomi.extension.ko.cookmana

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(options: List<String>) : Filter.Select<String>("장르", options.toTypedArray())

class SortFilter : Filter.Select<String>("정렬", SORT_OPTIONS.map { it.first }.toTypedArray()) {
    val selected: String
        get() = SORT_OPTIONS[state].second
}

private val SORT_OPTIONS = listOf(
    "인기" to "/api/popular/list",
    "최신" to "/api/lastest/list",
)
