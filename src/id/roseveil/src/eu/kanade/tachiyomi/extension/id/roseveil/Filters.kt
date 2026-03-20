package eu.kanade.tachiyomi.extension.id.roseveil

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    UriPartFilter(
        "Urutkan Berdasarkan",
        arrayOf(
            Pair("Paling Baru", "new"),
            Pair("Paling Banyak Dilihat", "views"),
            Pair("Rating Terbaik", "rating"),
            Pair("Judul", "title"),
        ),
    )

class OrderFilter :
    UriPartFilter(
        "Urutan",
        arrayOf(
            Pair("Menurun", "desc"),
            Pair("Meningkat", "asc"),
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
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipe",
        arrayOf(
            Pair("Semua", ""),
            Pair("Manga", "MANGA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
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
            Pair("Animals", "animals"),
            Pair("Boys Love", "boys-love"),
            Pair("Comedy", "comedy"),
            Pair("Crime", "crime"),
            Pair("Demon", "demon"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Game", "game"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magic", "magic"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Medical", "medical"),
            Pair("Mirror", "mirror"),
            Pair("Mystery", "mystery"),
            Pair("Office Workers", "office-workers"),
            Pair("Project", "project"),
            Pair("Psychological", "psychological"),
            Pair("Regression", "regression"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Revenge", "revenge"),
            Pair("Reverse Harem", "reverse-harem"),
            Pair("Romance", "romance"),
            Pair("Royalty", "royalty"),
            Pair("School Life", "school-life"),
            Pair("Sci Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Super Power", "super-power"),
            Pair("Supernatural", "supernatural"),
            Pair("Survival", "survival"),
            Pair("Thriller", "thriller"),
            Pair("Transmigration", "transmigration"),
            Pair("Yaoi", "yaoi"),
        ),
    )

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
