package eu.kanade.tachiyomi.extension.en.todaymanga

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    UriPartFilter(
        "Category",
        arrayOf(
            Pair("<select>", ""),
            Pair("Most Popular Manga", "most-popular"),
            Pair("Highest Rated Manga", "highest-rated"),
            Pair("Trending This Week", "trending"),
            Pair("Recent Updated Manga", "recent"),
            Pair("Editors' Choices", "editor-pick"),
            Pair("Completed Comedy Manga", "completed-comedy-manga"),
            Pair("Completed Drama Manga", "completed-drama-manga"),
            Pair("Completed Fantasy Manga", "completed-fantasy-manga"),
            Pair("Completed Romance Manga", "completed-romance-manga"),
        ),
    )

// The site doesn't seem to list all available genres, so instead the genres
// were sampled from the first 5 pages of recently updated
class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<select>", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("One Shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Reverse Harem", "reverse-harem"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Shounen", "shounen"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Vampire", "vampire"),
            Pair("Webtoons", "webtoons"),
        ),
    )

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
