package eu.kanade.tachiyomi.extension.en.jnovel

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Newest", ""),
            Pair("Oldest", "old"),
            Pair("A-Z", "asc"),
            Pair("Z-A", "desc"),
        ),
    )

class LabelFilter :
    SelectFilter(
        "Labels",
        arrayOf(
            Pair("All Labels", ""),
            Pair("J-Novel Club", "club"),
            Pair("J-Novel Heart", "heart"),
            Pair("J-Novel Pulp", "pulp"),
            Pair("J-Novel Knight", "knight"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Publication Status",
        arrayOf(
            Pair("Show All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Complete", "complete"),
            Pair("On Hiatus", "inactive"),
        ),
    )

class RentalFilter :
    SelectFilter(
        "Rentals",
        arrayOf(
            Pair("Show All", ""),
            Pair("Available", "available"),
            Pair("Unavailable", "unavailable"),
        ),
    )
