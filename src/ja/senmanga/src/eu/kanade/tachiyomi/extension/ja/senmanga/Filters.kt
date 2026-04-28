package eu.kanade.tachiyomi.extension.ja.senmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
        ),
    )

class OrderFilter :
    UriPartFilter(
        "Order",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "A-Z"),
            Pair("Z-A", "Z-A"),
            Pair("Update", "Update"),
            Pair("Added", "Added"),
            Pair("Popular", "Popular"),
            Pair("Rating", "Rating"),
        ),
    )
