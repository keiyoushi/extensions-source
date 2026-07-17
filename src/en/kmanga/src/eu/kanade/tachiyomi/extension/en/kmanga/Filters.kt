package eu.kanade.tachiyomi.extension.en.kmanga

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            Pair("Romance･Romcom", "1"),
            Pair("Horror･Mystery･Suspense", "2"),
            Pair("Gag･Comedy･Slice-of-Life", "3"),
            Pair("SF･Fantasy", "4"),
            Pair("Sports", "5"),
            Pair("Drama", "6"),
            Pair("Outlaws･Underworld･Punks", "7"),
            Pair("Action･Battle", "8"),
            Pair("Isekai･Super Powers", "9"),
            Pair("One-off Books", "10"),
            Pair("Shojo/josei", "11"),
            Pair("Yaoi/BL", "12"),
            Pair("LGBTQ", "13"),
            Pair("Yuri/GL", "14"),
            Pair("Anime", "15"),
            Pair("Award Winner", "16"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
