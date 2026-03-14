package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun selectedValue() = vals[state].second
}

class OrderFilter :
    SelectFilter(
        "Order",
        arrayOf(
            Pair("DESC", "desc"),
            Pair("ASC", "asc"),
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("New", "new"),
            Pair("Top Views", "views"),
            Pair("Top Rate", "rate"),
            Pair("Top Bookmark", "bookmark"),
            Pair("Title A-Z", "az"),
            Pair("Title Z-A", "za"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ONGOING"),
            Pair("Completed", "COMPLETED"),
            Pair("Hiatus", "HIATUS"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "MANGA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
        ),
    )

class ColorFilter :
    SelectFilter(
        "Color",
        arrayOf(
            Pair("All", ""),
            Pair("Full Color", "FULL_COLOR"),
            Pair("B&W", "BW"),
        ),
    )

class ReadingFilter :
    SelectFilter(
        "Reading",
        arrayOf(
            Pair("All", ""),
            Pair("Vertical Scroll", "VERTICAL_SCROLL"),
            Pair("Page", "PAGE"),
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Aksi", "aksi"),
            Pair("Arts", "arts"),
            Pair("Bl", "bl"),
            Pair("Boys Love", "boys-love"),
            Pair("Boyslove", "boyslove"),
            Pair("Comedy", "comedy"),
            Pair("Crybaby Seme", "crybaby-seme"),
            Pair("Dll", "dll"),
            Pair("Drama", "drama"),
            Pair("Fantasi Modern", "fantasi-modern"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Gong Bucin", "gong-bucin"),
            Pair("Gong Lebih Tua", "gong-lebih-tua"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Investigasi Kasus", "investigasi-kasus"),
            Pair("Investigasikasus", "investigasikasus"),
            Pair("Isekai", "isekai"),
            Pair("Martial", "martial"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Napolitana Ghost Story", "napolitana-ghost-story"),
            Pair("Office", "office"),
            Pair("Okultisme", "okultisme"),
            Pair("Psychological", "psychological"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Revenge", "revenge"),
            Pair("Romance", "romance"),
            Pair("Royalty", "royalty"),
            Pair("Salvation", "salvation"),
            Pair("School Life", "school-life"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Smut Supernatural Yaoi", "smut-supernatural-yaoi"),
            Pair("Su Aktif", "su-aktif"),
            Pair("Su Cinta Bertepuk Sebelah Tangan", "su-cinta-bertepuk-sebelah-tangan"),
            Pair("Su Luka Masa Lalu", "su-luka-masa-lalu"),
            Pair("Su Menggemaskan", "su-menggemaskan"),
            Pair("Supernatural", "supernatural"),
            Pair("Xianxia", "xianxia"),
            Pair("Yaoi", "yaoi"),
            Pair("Yaoi Bl", "yaoi-bl"),
        ),
    )

class TextFilter(name: String, val queryKey: String) : Filter.Text(name)
