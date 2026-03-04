package eu.kanade.tachiyomi.extension.en.kodansha

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            Pair("New and Popular", "0"),
            Pair("A-Z", "9"),
            Pair("Z-A", "8"),
            Pair("Newest", "5"),
            Pair("Oldest", "6"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "0"),
            Pair("Completed", "1"),
        ),
    )

class GenreFilter :
    Filter.Group<CheckBox>(
        "Genres",
        listOf(
            CheckBox("Action & Adventure", "12"),
            CheckBox("Arts & Entertainment", "17"),
            CheckBox("Biography", "21"),
            CheckBox("Comedy", "9"),
            CheckBox("Crafts", "28"),
            CheckBox("Drama", "1"),
            CheckBox("Fantasy", "4"),
            CheckBox("Fiction & Literature", "18"),
            CheckBox("Food", "14"),
            CheckBox("Games", "33"),
            CheckBox("General Nonfiction", "25"),
            CheckBox("Historical", "15"),
            CheckBox("History & Politics", "34"),
            CheckBox("Horror", "6"),
            CheckBox("Isekai", "16"),
            CheckBox("Language", "32"),
            CheckBox("LGBTQ", "20"),
            CheckBox("Made into Anime", "5"),
            CheckBox("Martial Arts", "30"),
            CheckBox("Movie/TV Tie-in", "19"),
            CheckBox("Philosophy", "31"),
            CheckBox("Reference", "29"),
            CheckBox("Religion & Spirituality", "22"),
            CheckBox("Romance", "2"),
            CheckBox("School Life", "8"),
            CheckBox("Science-Fiction", "7"),
            CheckBox("Slice of Life", "10"),
            CheckBox("Sports", "11"),
            CheckBox("Supernatural", "35"),
            CheckBox("Thriller", "13"),
            CheckBox("Videogame Tie-in", "24"),
            CheckBox("Yaoi/BL", "3"),
            CheckBox("Yuri", "23"),
        ),
    )

class AgeRatingFilter :
    Filter.Group<Filter.CheckBox>(
        "Age Rating",
        listOf(
            CheckBox("10+", "10"),
            CheckBox("13+", "13"),
            CheckBox("16+", "16"),
            CheckBox("18+", "18"),
        ),
    )

class CheckBox(displayName: String, val value: String) : Filter.CheckBox(displayName)

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
