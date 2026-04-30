package eu.kanade.tachiyomi.extension.en.manhwabuddy

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class GenreFilter :
    UriPartFilter(
        "Genres",
        arrayOf(
            Pair("Action", "action"),
            Pair("Romance", "romance"),
            Pair("Drama", "drama"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("School Life", "school-life"),
            Pair("Smut", "smut"),
            Pair("Isekai", "isekai"),
            Pair("Thriller", "thriller"),
            Pair("Crime", "crime"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Horror", "horror"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Sports", "sports"),
        ),
    )
