package eu.kanade.tachiyomi.extension.en.allanime

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(name: String, private val options: List<Pair<String, String>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun getValue() = options[state].second.takeUnless { it.isEmpty() }
}

internal class SortFilter(name: String, sorts: List<Pair<String, String>>) : SelectFilter(name, sorts)

internal class CountryFilter(name: String, countries: List<Pair<String, String>>) : SelectFilter(name, countries)

internal class Genre(name: String) : Filter.TriState(name)

internal class GenreFilter(title: String, genres: List<String>) :
    Filter.Group<Genre>(title, genres.map(::Genre)) {
    val included: List<String>?
        get() = state.filter { it.isIncluded() }.map { it.name }.takeUnless { it.isEmpty() }

    val excluded: List<String>?
        get() = state.filter { it.isExcluded() }.map { it.name }.takeUnless { it.isEmpty() }
}

private val sortList = listOf(
    Pair("Update", ""),
    Pair("Name Ascending", "Name_ASC"),
    Pair("Name Descending", "Name_DESC"),
)

private val countryList: List<Pair<String, String>> = listOf(
    Pair("All", "ALL"),
    Pair("Japan", "JP"),
    Pair("China", "CN"),
    Pair("Korea", "KR"),
)

private val genreList: List<String> = listOf(
    "4 Koma",
    "Action",
    "Adult",
    "Adventure",
    "Cars",
    "Comedy",
    "Cooking",
    "Crossdressing",
    "Dementia",
    "Demons",
    "Doujinshi",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Game",
    "Gender Bender",
    "Gyaru",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Josei",
    "Kids",
    "Loli",
    "Magic",
    "Manhua",
    "Manhwa",
    "Martial Arts",
    "Mature",
    "Mecha",
    "Medical",
    "Military",
    "Monster Girls",
    "Music",
    "Mystery",
    "One Shot",
    "Parody",
    "Police",
    "Post Apocalyptic",
    "Psychological",
    "Reincarnation",
    "Reverse Harem",
    "Romance",
    "Samurai",
    "School",
    "Sci-Fi",
    "Seinen",
    "Shota",
    "Shoujo",
    "Shoujo Ai",
    "Shounen",
    "Shounen Ai",
    "Slice of Life",
    "Smut",
    "Space",
    "Sports",
    "Super Power",
    "Supernatural",
    "Suspense",
    "Thriller",
    "Tragedy",
    "Unknown",
    "Vampire",
    "Webtoons",
    "Yaoi",
    "Youkai",
    "Yuri",
    "Zombies",
)

fun getFilters() = FilterList(
    SortFilter("Sort", sortList),
    CountryFilter("Countries", countryList),
    GenreFilter("Genres", genreList),
)
