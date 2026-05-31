package eu.kanade.tachiyomi.extension.en.emaqi

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            Pair("Shonen", "shonen"),
            Pair("Shojo", "shojo"),
            Pair("Seinen", "seinen"),
            Pair("Kids", "kids"),
            Pair("Josei", "josei"),
            Pair("Artbook", "artbook"),
            Pair("Free One-Shot", "one-shot"),
            Pair("BL / Yaoi", "bl"),
            Pair("Thriller", "suspense"),
            Pair("Mystery", "mystery"),
            Pair("Adventure", "adventure"),
            Pair("Drama", "drama"),
            Pair("GL / Yuri", "yuri"),
            Pair("Sports", "sports"),
            Pair("Food", "food"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Isekai", "isekai"),
            Pair("Action", "action"),
            Pair("Fantasy", "fantasy"),
            Pair("Horror", "horror"),
            Pair("Romance", "romance"),
            Pair("Comedy", "comedy"),
            Pair("Death Game", "death-game"),
            Pair("War", "war"),
            Pair("Rom-com", "rom-com"),
            Pair("Travel", "travel"),
            Pair("Nature", "nature"),
            Pair("Showbiz", "showbiz"),
            Pair("Educational", "educational"),
            Pair("Medical", "medical"),
            Pair("Animal", "animal"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Supernatural", "supernatural"),
            Pair("Art", "art"),
            Pair("Gamble", "gamble"),
            Pair("Depressing", "depressing"),
            Pair("Professional", "profession"),
            Pair("Survival", "survival"),
            Pair("Hobby", "hobby"),
            Pair("History", "history"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
