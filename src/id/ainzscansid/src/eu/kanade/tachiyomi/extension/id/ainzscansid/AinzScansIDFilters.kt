package eu.kanade.tachiyomi.extension.id.ainzscansid

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun selectedValue() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Latest", "latest"),
            Pair("Newest", "new"),
            Pair("Top Views", "views"),
            Pair("Top Rating", "rate"),
            Pair("Top Bookmark", "bookmark"),
            Pair("Title A-Z", "az"),
            Pair("Title Z-A", "za"),
        ),
    )

class OrderFilter :
    SelectFilter(
        "Order By",
        arrayOf(
            Pair("Descending", "desc"),
            Pair("Ascending", "asc"),
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

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Beasts", "beasts"),
            Pair("Comedy", "comedy"),
            Pair("Cooking", "cooking"),
            Pair("Crime", "crime"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Gore", "gore"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magic", "magic"),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Military", "military"),
            Pair("Monsters", "monsters"),
            Pair("Murim", "murim"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shotacon", "shotacon"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Survival", "survival"),
            Pair("System", "system"),
            Pair("Thriller", "thriller"),
            Pair("Tragedy", "tragedy"),
            Pair("Wuxia", "wuxia"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manhua", "MANHUA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Manga", "MANGA"),
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
        "Reading Mode",
        arrayOf(
            Pair("All", ""),
            Pair("Vertical Scroll", "VERTICAL_SCROLL"),
            Pair("Page", "PAGE"),
        ),
    )

class TextFilter(name: String, val queryKey: String) : Filter.Text(name)
