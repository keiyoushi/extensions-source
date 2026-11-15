package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Order",
        arrayOf(
            "Latest update",
            "New manhwa",
            "Most view",
            "Most like",
            "A - Z",
            "Z - A",
        ),
    ) {
    fun selectedValue() = when (state) {
        0 -> "update"
        1 -> "new"
        2 -> "top"
        3 -> "like"
        4 -> "az"
        5 -> "za"
        else -> "update"
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf(
            "All",
            "Ongoing",
            "On hold",
            "Completed",
        ),
    ) {
    fun selectedValue() = state.toString()
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

val genreList = listOf(
    Genre("Adult", "4"),
    Genre("Doujinshi", "9"),
    Genre("Harem", "17"),
    Genre("Manga", "24"),
    Genre("Manhwa", "26"),
    Genre("Mature", "28"),
    Genre("NTR", "33"),
    Genre("Romance", "36"),
    Genre("Webtoon", "57"),
    Genre("Action", "59"),
    Genre("Comedy", "60"),
    Genre("BL", "61"),
    Genre("Horror", "62"),
    Genre("Raw", "63"),
    Genre("Uncensore", "64"),
    Genre("Art", "65"),
    Genre("M18Scan", "66"),
    Genre("Drama", "68"),
    Genre("Supernatural", "128"),
    Genre("Seinen", "160"),
    Genre("Borderline H", "161"),
    Genre("Full Color", "162"),
    Genre("Slice of Life", "163"),
    Genre("Smut", "164"),
    Genre("Uncensored", "165"),
    Genre("Webtoons", "166"),
    Genre("Explicit Sex", "167"),
    Genre("Cohabitation", "168"),
    Genre("Delinquents", "169"),
    Genre("Fetish", "170"),
    Genre("Nudity", "171"),
    Genre("Sexual Abuse", "172"),
    Genre("Sexual Content", "173"),
    Genre("Fantasy", "174"),
    Genre("Ghosts", "175"),
    Genre("Historical", "176"),
    Genre("School Life", "177"),
    Genre("Psychological", "178"),
    Genre("Incest", "179"),
    Genre("Japanese Webtoons", "180"),
    Genre("Coworkers", "181"),
    Genre("Salaryman", "182"),
    Genre("Siblings", "183"),
    Genre("Work Life", "184"),
    Genre("Gyaru", "185"),
    Genre("Based on Another Work", "186"),
    Genre("Demons", "187"),
    Genre("Crime", "188"),
    Genre("Mystery", "189"),
    Genre("Reverse Harem", "190"),
    Genre("Adventure", "191"),
    Genre("Isekai", "192"),
    Genre("Magic", "193"),
    Genre("Thriller", "194"),
    Genre("Time Travel", "195"),
    Genre("Reincarnation", "196"),
    Genre("Sports", "197"),
    Genre("Medical", "198"),
    Genre("Sci Fi", "199"),
    Genre("AI Art", "200"),
    Genre("Animal Characteristics", "201"),
    Genre("Monster Girls", "202"),
    Genre("Violence", "203"),
    Genre("Collection of Stories", "204"),
    Genre("Ecchi", "205"),
    Genre("Monsters", "206"),
    Genre("Survival", "207"),
)
