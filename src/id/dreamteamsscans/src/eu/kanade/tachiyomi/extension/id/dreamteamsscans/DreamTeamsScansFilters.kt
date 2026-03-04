package eu.kanade.tachiyomi.extension.id.dreamteamsscans

import eu.kanade.tachiyomi.source.model.Filter

abstract class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Urutkan berdasarkan",
        arrayOf(
            Pair("Populer", "popular"),
            Pair("Terbaru", "new"),
            Pair("Update", "update"),
            Pair("A-Z", "title"),
            Pair("Rating", "rating"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ONGOING"),
            Pair("Completed", "COMPLETED"),
            Pair("Hiatus", "HIATUS"),
            Pair("Cancelled", "CANCELLED"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipe",
        arrayOf(
            Pair("Semua", ""),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
            Pair("Manga", "MANGA"),
        ),
    )

class ColorFilter :
    UriPartFilter(
        "Format Warna",
        arrayOf(
            Pair("Semua", ""),
            Pair("Full Color", "FULL_COLOR"),
            Pair("Hitam Putih", "BLACK_AND_WHITE"),
        ),
    )

class ReadingFormatFilter :
    UriPartFilter(
        "Format Baca",
        arrayOf(
            Pair("Semua", ""),
            Pair("Vertical Scroll", "VERTICAL_SCROLL"),
            Pair("Horizontal Paginated", "HORIZONTAL_PAGINATED"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Semua", ""),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci Fi", "sci-fi"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Straight", "straight"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Yaoi", "yaoi"),
        ),
    )
