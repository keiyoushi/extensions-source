package eu.kanade.tachiyomi.extension.en.mangade

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Newest", "newest"),
            Pair("Oldest", "oldest"),
            Pair("Most viewed", "most-viewed"),
            Pair("Highly rate", "rating"),
            Pair("Name A-Z", "a-z"),
            Pair("Name Z-A", "z-a"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Completed", "1"),
            Pair("Releasing", "2"),
            Pair("On Hiatus", "3"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("One-Shot", "one-shot"),
            Pair("Dounjinshi", "dounjinshi"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
        ),
    )

class YearFilter :
    UriPartFilter(
        "Year",
        arrayOf(Pair("All", "")) + (2026 downTo 2019).map {
            Pair(it.toString(), it.toString())
        }.toTypedArray(),
    )

class ChapterCountFilter :
    UriPartFilter(
        "Chapters",
        arrayOf(
            Pair(">=0", "0"),
            Pair(">=50", "50"),
            Pair(">=100", "100"),
            Pair(">=150", "150"),
            Pair(">=200", "200"),
            Pair(">=250", "250"),
        ),
    )

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
class Genre(name: String, val id: String) : Filter.CheckBox(name)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
