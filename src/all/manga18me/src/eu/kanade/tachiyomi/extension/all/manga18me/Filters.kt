package eu.kanade.tachiyomi.extension.all.manga18me

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header(name = "The filter is ignored when using text search."),
        GenreFilter("Genre", getGenresList),
        SortFilter("Sort", getSortsList),
        RawFilter("Raw"),
        CompletedFilter("Completed"),
    )
}

/** Filters **/

internal class GenreFilter(name: String, genreList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, genreList, state)

internal class SortFilter(name: String, sortList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, sortList, state)

internal class CompletedFilter(name: String) : CheckBoxFilter(name)

internal class RawFilter(name: String) : CheckBoxFilter(name)

internal open class CheckBoxFilter(name: String, val value: String = "") : Filter.CheckBox(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

/** Filters Data **/
private val getGenresList: List<Pair<String, String>> = listOf(
    Pair("Manga", "manga"),
    Pair("Drama", "drama"),
    Pair("Mature", "mature"),
    Pair("Romance", "romance"),
    Pair("Adult", "adult"),
    Pair("Hentai", "hentai"),
    Pair("Comedy", "comedy"),
    Pair("Ecchi", "ecchi"),
    Pair("School Life", "school-life"),
    Pair("Shounen", "shounen"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Seinen", "seinen"),
    Pair("Yuri", "yuri"),
    Pair("Action", "action"),
    Pair("Fantasy", "fantasy"),
    Pair("Harem", "harem"),
    Pair("Supernatural", "supernatural"),
    Pair("Sci-Fi", "sci-fi"),
    Pair("Isekai", "isekai"),
    Pair("Shoujo", "shoujo"),
    Pair("Horror", "horror"),
    Pair("Psychological", "psychological"),
    Pair("Smut", "smut"),
    Pair("Tragedy", "tragedy"),
    Pair("Raw", "raw"),
    Pair("Historical", "historical"),
    Pair("Adventure", "adventure"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Manhwa", "manhwa"),
    Pair("Manhua", "manhua"),
    Pair("Mystery", "mystery"),
    Pair("BL", "bl"),
    Pair("Yaoi", "yaoi"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Thriller", "thriller"),
    Pair("Josei", "josei"),
    Pair("Sports", "sports"),
    Pair("GL", "gl"),
    Pair("Family", "family"),
    Pair("Magic", "magic"),
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Latest", "latest"),
    Pair("A-Z", "alphabet"),
    Pair("Rating", "rating"),
    Pair("Trending", "trending"),
)
