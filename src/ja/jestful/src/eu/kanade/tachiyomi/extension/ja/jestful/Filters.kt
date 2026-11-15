package eu.kanade.tachiyomi.extension.ja.jestful

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

// ============================== Filters ===============================
class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class Status : Filter.Select<String>("Status", arrayOf("Any", "Ongoing", "Completed")) {
    val mStatus: String get() = arrayOf("", "2", "1")[state]
}

class TextField(name: String, val key: String) : Filter.Text(name)

class SortBy : Filter.Sort("Sort by", arrayOf("Name", "Most read", "Last updated"), Selection(2, false)) {
    val sort: String
        get() = when (state?.index) {
            0 -> "name"
            1 -> "views"
            else -> "last_update"
        }

    val sortType: String get() = if (state?.ascending == true) "ASC" else "DESC"
}

// ================================ Genres ================================
private val genres = listOf(
    Genre("Action", "action"),
    Genre("Adult", "adult"),
    Genre("Adventure", "adventure"),
    Genre("Comedy", "comedy"),
    Genre("Demons", "demons"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Magic", "magic"),
    Genre("Mature", "mature"),
    Genre("Mecha", "mecha"),
    Genre("Mystery", "mystery"),
    Genre("Psychological", "psychological"),
    Genre("Romance", "romance"),
    Genre("School Life", "school-life"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Super Power", "super-power"),
    Genre("Supernatural", "supernatural"),
    Genre("Tragedy", "tragedy"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)

// ============================== Filter list ==============================
fun jestfulFilters() = FilterList(
    SortBy(),
    GenreList(genres),
    Status(),
    TextField("Author", "author"),
    TextField("Group", "group"),
)
