package eu.kanade.tachiyomi.extension.en.mangauno

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Webtoon", "webtoon"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Popularity", "popularity"),
            Pair("Score", "score"),
            Pair("Latest chapter", "latest"),
            Pair("A–Z", "az"),
            Pair("Newest added", "newest"),
            Pair("Oldest added", "oldest"),
        ),
    )

class YearFilter(name: String) : Filter.Text(name)

class YearGroup :
    Filter.Group<YearFilter>(
        "Year (1900-${Calendar.getInstance().get(Calendar.YEAR)})",
        listOf(
            YearFilter("Min"),
            YearFilter("Max"),
        ),
    )

class AdultFilter : Filter.CheckBox("Include adult (18+)", false)

class CheckBoxFilter(name: String) : Filter.CheckBox(name)
class GenreGroup(genres: List<CheckBoxFilter>) : Filter.Group<CheckBoxFilter>("Genres", genres)
class TagGroup(tags: List<CheckBoxFilter>) : Filter.Group<CheckBoxFilter>("Tags", tags)
