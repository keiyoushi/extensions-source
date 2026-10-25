package eu.kanade.tachiyomi.extension.all.inkrush

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class CategoryFilter :
    UriPartFilter(
        "Category",
        arrayOf(
            "Trending" to "trending",
            "Latest updates" to "latest",
            "New" to "new",
            "Popular" to "popular",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "All" to "",
            "Manga" to "ja",
            "Manhwa" to "ko",
            "Manhua" to "zh",
            "Other / Webtoons" to "other",
        ),
    )
