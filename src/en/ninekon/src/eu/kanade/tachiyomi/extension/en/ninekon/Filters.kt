package eu.kanade.tachiyomi.extension.en.ninekon

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Sort("Sort", arrayOf("Date", "Title", "Rate", "Views"), Selection(0, false))

class GenreFilter : Filter.Group<GenreCheckBox>("Genres", genres.map { GenreCheckBox(it.first, it.second) })

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

// Sourced from tags API HAR response
private val genres = listOf(
    Pair("Action", "action"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Comedy", "comedy"),
    Pair("Cooking", "cooking"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Erotica", "erotica"),
    Pair("Fantasy", "fantasy"),
    Pair("Gender bender", "gender-bender"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Mystery", "mystery"),
    Pair("One Shot", "one-shot"),
    Pair("Psychological", "psychological"),
    Pair("Romance", "romance"),
    Pair("School Life", "school-life"),
    Pair("Sci Fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Shounen", "shounen"),
    Pair("Shounen Ai", "shounen-ai"),
    Pair("Slice of life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sports", "sports"),
    Pair("Supernatural", "supernatural"),
    Pair("Tragedy", "tragedy"),
    Pair("Webtoons", "webtoons"),
    Pair("Yaoi", "yaoi"),
    Pair("Yuri", "yuri"),
)
