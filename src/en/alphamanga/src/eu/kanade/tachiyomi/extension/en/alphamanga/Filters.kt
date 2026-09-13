package eu.kanade.tachiyomi.extension.en.alphamanga

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    SelectFilter(
        "Progress",
        arrayOf(
            "All" to "",
            "Ongoing" to "2",
            "Completed" to "1",
            "Suspended" to "3",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            "All" to "",
            "Shonen" to "1001",
            "Shojo" to "1002",
            "Romance" to "1033",
            "Action" to "1003",
            "Villainess" to "1037",
            "Reincarnation" to "1005",
            "Slice of Life" to "1057",
            "Anime" to "1041",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
