package eu.kanade.tachiyomi.extension.all.mangadraft

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

// sort is only applied when all has been selected in order
class SortFilter(state: Int = 0) : UriPartFilter(
    "Sort",
    arrayOf(
        Pair("Likes", "likes"),
        Pair("Comments", "comments"),
        Pair("Views", "views"),
        Pair("Name", "name"),
    ),
    state,
)
class TypeFilter(state: Int = 0) : UriPartFilter(
    "Type",
    arrayOf(
        Pair("All", "all"),
        Pair("Manga-Comics", "bd.manga"),
        Pair("Webtoons", "webtoons"),
        Pair("Light Novels", "novels"),
        Pair("ArtBooks", "artbooks"),
    ),
    state,
)
class OrderFilter(state: Int = 0) : UriPartFilter(
    "Order",
    arrayOf(
        Pair("All", "all"),
        Pair("Popularity", "popular"),
        Pair("Trending", "trending"),
        Pair("Recently Released", "news"),
    ),
    state,
)
class SectionFilter(state: Int = 0) : UriPartFilter(
    "Section",
    arrayOf(
        Pair("All", ""),
        Pair("Neoville", "neoville"),
        Pair("Original", "original"),
        Pair("Indepolis", "indepolis"),
        Pair("Recently Released", "news"),
    ),
    state,
)
class StatusFilter(state: Int = 0) : UriPartFilter(
    "Status",
    arrayOf(
        Pair("All", ""),
        Pair("In Progress", "0"),
        Pair("Completed", "1"),
        Pair("On Break", "2"),
    ),
    state,
)
class GenreFilter(state: Int = 0) : UriPartFilter(
    "Genre",
    arrayOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Adventure", "Adventure"),
        Pair("BL", "boys-love-yaoi"),
        Pair("Drama", "drama"),
        Pair("Geek", "geek"),
        Pair("Yuri", "girls-love-yuri"),
        Pair("Historic", "historic"),
        Pair("Horror", "horror"),
        Pair("Humor", "humor"),
        Pair("Teens", "teens"),
        Pair("Thriller", "thriller"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("Fantasy", "fantasy"),
        Pair("Sport", "sport"),
        Pair("Super-hero", "super-hero"),
        Pair("Slice-of-life", "slice-of-life"),
        Pair("Western", "western"),
    ),
    state,
)
class FormatFilter(state: Int = 0) : UriPartFilter(
    "Format",
    arrayOf(
        Pair("All", ""),
        Pair("Series", "serie"),
        Pair("One-Shot", "oneshot"),
    ),
    state,
)
class LanguageFilter(state: Int = 0) : UriPartFilter(
    "Language",
    arrayOf(
        Pair("All", ""),
        Pair("Deutsch", "de"),
        Pair("English", "en"),
        Pair("Spanish", "es"),
        Pair("French", "fr"),
        Pair("Italian", "it"),
        Pair("Polski", "pl"),
        Pair("Portuguese", "pt"),
        Pair("Suomen kieli", "fi"),
        Pair("Japanese", "jp"),
    ),
    state,
)
