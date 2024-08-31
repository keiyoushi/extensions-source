package eu.kanade.tachiyomi.extension.en.mangademon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class StatusFilter : SelectFilter("Status", status) {
    companion object {
        private val status = listOf(
            Pair("All", "all"),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        )
    }
}

class SortFilter : SelectFilter("Sort", sort) {
    companion object {
        private val sort = listOf(
            Pair("Top Views", "VIEWS DESC"),
            Pair("A To Z", "NAME ASC"),
        )
    }
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

class GenreFilter : Filter.Group<CheckBoxFilter>(
    "Genre",
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }

    companion object {
        private val genres = listOf(
            Pair("Action", "1"),
            Pair("Adventure", "2"),
            Pair("Comedy", "3"),
            Pair("Cooking", "34"),
            Pair("Doujinshi", "25"),
            Pair("Drama", "4"),
            Pair("Ecchi", "19"),
            Pair("Fantasy", "5"),
            Pair("Gender Bender", "30"),
            Pair("Harem", "10"),
            Pair("Historical", "28"),
            Pair("Horror", "8"),
            Pair("Isekai", "33"),
            Pair("Josei", "31"),
            Pair("Martial Arts", "6"),
            Pair("Mature", "22"),
            Pair("Mecha", "32"),
            Pair("Mystery", "15"),
            Pair("One Shot", "26"),
            Pair("Psychological", "11"),
            Pair("Romance", "12"),
            Pair("School Life", "13"),
            Pair("Sci-fi", "16"),
            Pair("Seinen", "17"),
            Pair("Shoujo", "14"),
            Pair("Shoujo Ai", "23"),
            Pair("Shounen", "7"),
            Pair("Shounen Ai", "29"),
            Pair("Slice of Life", "21"),
            Pair("Smut", "27"),
            Pair("Sports", "20"),
            Pair("Supernatural", "9"),
            Pair("Tragedy", "18"),
            Pair("Webtoons", "24"),
        )
    }
}

fun getFilters() = FilterList(
    Filter.Header("Ignored when using text search"),
    Filter.Separator(),
    SortFilter(),
    StatusFilter(),
    GenreFilter(),
)
