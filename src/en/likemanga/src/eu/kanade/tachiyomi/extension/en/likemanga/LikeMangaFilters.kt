package eu.kanade.tachiyomi.extension.en.likemanga

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

class GenreFilter(
    name: String,
    genres: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class SortFilter(default: String? = null) : SelectFilter(
    "Sort By",
    listOf(
        Pair("", ""),
        Pair("Lasted update", "lastest-chap"),
        Pair("Lasted manga", "lastest-manga"),
        Pair("Top all", "top-manga"),
        Pair("Top month", "top-month"),
        Pair("Top week", "top-week"),
        Pair("Top day", "top-day"),
        Pair("Follow", "follow"),
        Pair("Comments", "comment"),
        Pair("Number of Chapters", "num-chap"),
    ),
    default,
)

class StatusFilter : SelectFilter(
    "Status",
    listOf(
        Pair("All", ""),
        Pair("Complete", "Complete"),
        Pair("In process", "In process"),
        Pair("Pause", "Pause"),
    ),
)

class ChapterCountFilter : SelectFilter(
    "Number of Chapters",
    listOf(
        Pair("", ""),
        Pair(">= 0 chapter", "1"),
        Pair(">= 50 chapter", "50"),
        Pair(">= 100 chapter", "100"),
        Pair(">= 200 chapter", "200"),
        Pair(">= 300 chapter", "300"),
        Pair(">= 400 chapter", "400"),
        Pair(">= 500 chapter", "500"),
    ),
)
