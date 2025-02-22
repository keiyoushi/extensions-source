package eu.kanade.tachiyomi.extension.en.taadd

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TriStateFilter(
    name: String,
    val value: String,
) : Filter.TriState(name)

open class TriStateFilterGroup(
    name: String,
    genres: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    genres.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

open class TextFilter(name: String) : Filter.Text(name)

open class TextGroupFilter(name: String) : Filter.Group<Filter<*>>(
    name,
    listOf(
        TextFilter("$name Name"),
        Selection("Match"),
    ),
) {
    val text get() = (state[0] as TextFilter).state.trim()
    val select get() = (state[1] as Selection).selected
}

open class Selection(name: String) : SelectFilter(
    name,
    listOf(
        "Contains" to "contain",
        "Beginning" to "begin",
        "End" to "end",
    ),
)

class NameMatchFilter : Selection("Query Match")

class AuthorFilter : TextGroupFilter("Author")
class ArtistFilter : TextGroupFilter("Artist")

class GenreFilter : TriStateFilterGroup(
    "Genres",
    listOf(
        "Romance" to "24",
        "Comedy" to "4",
        "Drama" to "6",
        "Fantasy" to "8",
        "Action" to "1",
        "Slice Of Life" to "32",
        "School Life" to "25",
        "Shoujo" to "28",
        "Adventure" to "2",
        "Yaoi" to "40",
        "Shounen" to "30",
        "Supernatural" to "34",
        "Seinen" to "27",
        "Historical" to "11",
        "One Shot" to "22",
        "Doujinshi" to "45",
        "Mystery" to "21",
        "Shounen Ai" to "42",
        "Ecchi" to "7",
    ),
)

class CompletedSeriesFilter : SelectFilter(
    "Completed Series?",
    listOf(
        "Either" to "either",
        "Yes" to "yes",
        "No" to "no",
    ),
)

class ReleaseYearFilter : SelectFilter(
    "Release",
    buildList {
        add("All" to "0")
        val currentYear = year.format(Date()).toInt()
        (currentYear downTo 1995).forEach { add(it.toString() to it.toString()) }
    },
)

private val year = SimpleDateFormat("yyyy", Locale.ENGLISH)

fun getFilters() = FilterList(
    NameMatchFilter(),
    AuthorFilter(),
    ArtistFilter(),
    GenreFilter(),
    CompletedSeriesFilter(),
    ReleaseYearFilter(),
)
