package eu.kanade.tachiyomi.extension.en.manhwaxxl

import eu.kanade.tachiyomi.source.model.Filter

class Filters : Filter.Select<String>("Genre", genres.map { it.first }.toTypedArray()) {
    val selectedId: String
        get() = genres[state].second
}

private val genres = arrayOf(
    Pair("All", ""),
    Pair("Action", "action"),
    Pair("Adult", "adult"),
    Pair("BL", "bl"),
    Pair("Comedy", "comedy"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Harem", "harem"),
    Pair("Horror", "horror"),
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
    Pair("Mature", "mature"),
    Pair("NTR", "ntr"),
    Pair("Romance", "romance"),
    Pair("Uncensore", "uncensore"),
    Pair("Webtoon", "webtoon"),
)
