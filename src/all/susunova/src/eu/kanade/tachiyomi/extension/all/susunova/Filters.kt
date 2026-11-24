package eu.kanade.tachiyomi.extension.all.susunova

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Popular", "popular"),
            Pair("Latest", "latest"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Award Winning", "award-winning"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Hentai", "hentai"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Yaoi", "yaoi"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic"),
        ),
    )

class LanguageFilter :
    UriPartFilter(
        "Language",
        arrayOf(
            Pair("All", ""),
            Pair("Indonesia", "indonesia"),
            Pair("English", "english"),
            Pair("Japanese", "japanese"),
            Pair("Korean", "korean"),
            Pair("Chinese (Simplified)", "chinese_simplified"),
            Pair("Chinese (Traditional)", "chinese_traditional"),
        ),
    )

class NationFilter :
    UriPartFilter(
        "Nation",
        arrayOf(
            Pair("All", ""),
            Pair("Japan", "japan"),
            Pair("Korea", "korea"),
            Pair("Indonesia", "indonesia"),
            Pair("Western", "western"),
            Pair("China", "china"),
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
        ),
    )
