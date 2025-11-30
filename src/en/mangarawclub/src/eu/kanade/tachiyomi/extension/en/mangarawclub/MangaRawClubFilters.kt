package eu.kanade.tachiyomi.extension.en.mangarawclub

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter("Sort by", getSortsList),
        StatusFilter("Status", getStatusList),
        TypeFilter("Types", getTypeList),
        GenreFilter("Genre", getGenres),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        TextFilter("Tags"),
        Filter.Separator(),
        ChapterMinFilter("Minimum Chapter"),
        ChapterMaxFilter("Maximum Chapter"),
        Filter.Separator(),
        Filter.Header("Minimum Rating (e.g.: 1.1, 5.0)"),
        RatingFilter("Minimum Rating"),
        Filter.Separator(),
        ExtraFilter("Extras"),
    )
}

internal open class ExtraFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("Only completed series ", "only_completed"),
            Pair("At least 50+ chapters translated", "only_translated"),
            Pair("Hide long hiatus (> 6 months) ", "hide_on_break"),
        ).map { CheckBoxFilter(it.first, it.second) },
    )

internal open class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)
internal open class StatusFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state) {
    val selected get() = vals[state].replace("Any", "")
}

internal open class TypeFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state) {
    val selected get() = vals[state].replace("Any", "")
}

internal class GenreFilter(name: String, genreList: List<String>) :
    Filter.Group<TriFilter>(name, genreList.map { TriFilter(it) })

internal open class TriFilter(name: String) : Filter.TriState(name)

internal open class ChapterMinFilter(name: String) : Filter.Text(name)

internal open class ChapterMaxFilter(name: String) : Filter.Text(name)

internal open class RatingFilter(name: String) : Filter.Text(name)

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class SortFilter(name: String, val vals: List<Pair<String, String>>, state: Int = 1) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    val selected get() = vals[state].second.takeIf { it.isNotEmpty() }
}

private val getGenres = listOf(
    "Action",
    "Adventure",
    "Comedy",
    "Cooking",
    "Manga",
    "Drama",
    "Fantasy",
    "Gender Bender",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Josei",
    "Manhua",
    "Manhwa",
    "Martial Arts",
    "Mature",
    "Mecha",
    "Medical",
    "Mystery",
    "One Shot",
    "Psychological",
    "Romance",
    "School Life",
    "Sci Fi",
    "Seinen",
    "Shoujo",
    "Shounen",
    "Slice Of Life",
    "Sports",
    "Supernatural",
    "Tragedy",
    "Webtoons",
    "Ladies",
)

private val getStatusList = listOf(
    "Any",
    "Ongoing",
    "Completed",
    "Hiatus",
)

private val getTypeList = listOf(
    "Any",
    "Manga",
    "Manhwa",
    "Manhua",
    "Webtoon",
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("New", "recently_added"),
    Pair("Updated", "latest"),
    Pair("Popular (Daily)", "popular_daily"),
    Pair("Popular (Weekly)", "popular_weekly"),
    Pair("Popular (Monthly)", "popular_monthly"),
    Pair("Popular (All Time)", "popular_all_time"),
    Pair("Rating", "rating"),
    Pair("Title (A-Z)", "az"),
    Pair("Title (Z-A)", "za"),
)
