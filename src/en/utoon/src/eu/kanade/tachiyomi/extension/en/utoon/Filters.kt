package eu.kanade.tachiyomi.extension.en.utoon

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Latest", ""),
            Pair("Popular", "popular"),
            Pair("Newest", "new"),
            Pair("A-Z", "alphabet"),
        ),
    )

class UtoonStatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Fantasy", "fantasy"),
            Pair("Drama", "drama"),
            Pair("Adventure", "adventure"),
            Pair("Action", "action"),
            Pair("Comedy", "comedy"),
            Pair("Shounen", "shounen"),
            Pair("Comic", "comic"),
            Pair("Manhwa", "manhwa"),
            Pair("Fight", "fight"),
            Pair("Magic", "magic"),
            Pair("Supernatural", "supernatural"),
            Pair("Manga", "manga"),
            Pair("Romance", "romance"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Crime", "crime"),
            Pair("Hunters", "hunters"),
            Pair("Mystery", "mystery"),
            Pair("Isekai", "isekai"),
            Pair("Historical", "historical"),
            Pair("Mangatoon", "mangatoon"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Shoujo", "shoujo"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Manhua", "manhua"),
        ),
    )
