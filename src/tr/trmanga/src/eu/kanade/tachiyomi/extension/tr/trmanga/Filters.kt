@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.tr.trmanga

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

class SortFilter(state: Int = 0) :
    UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Popularity", "views"),
            Pair("Rating", "rating"),
            Pair("Date Updated", "updated"),
            Pair("Date Released", "released"),
            Pair("Alphabetical Order", "name"),
        ),
        state,
    )
class OrderFilter(state: Int = 0) :
    UriPartFilter(
        "Order",
        arrayOf(
            Pair("Descending", "DESC"),
            Pair("Ascending", "ASC"),
        ),
        state,
    )
class StatusFilter(state: Int = 0) :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "continues"),
            Pair("Completed", "complated"),
            Pair("Up to date", "uptodate"),
        ),
        state,
    )
class GenreFilter(state: Int = 0) :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Aksiyon", "aksiyon"),
            Pair("Bilim Kurgu", "bilim-kurgu"),
            Pair("BL", "bl"),
            Pair("Büyü", "buyu"),
            Pair("Doğaüstü", "dogaustu"),
            Pair("Dövüş Sanatları", "dovus-sanatlari"),
            Pair("Dram", "dram"),
            Pair("Fantastik", "fantastik"),
            Pair("Gerilim", "gerilim"),
            Pair("Gizem", "gizem"),
            Pair("GL", "gl"),
            Pair("Harem", "harem"),
            Pair("İsekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Komedi", "komedi"),
            Pair("Korku", "korku"),
            Pair("Macera", "macera"),
            Pair("Manga", "manga"),
            Pair("Okul", "okul"),
            Pair("One-shot", "one-shot"),
            Pair("Oyun", "oyun"),
            Pair("Psikolojik", "pskolojik"),
            Pair("Reenkarnasyon", "reenkarnasyon"),
            Pair("Romantik", "romantik"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Spor", "spor"),
            Pair("Suç", "suc"),
            Pair("Süper Kahraman", "super-kahraman"),
            Pair("Tarih", "tarih"),
            Pair("Trajedi", "trajedi"),
            Pair("Vampir", "vampir"),
            Pair("Yaoi", "yaoi"),
            Pair("Yetişkin", "yetiskin"),
            Pair("Yuri", "yuri"),
            Pair("Zaman Yolculuğu", "zaman-yolculugu"),
        ),
        state,
    )
