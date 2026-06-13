package eu.kanade.tachiyomi.extension.en.lusttoon

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected: String
        get() = options[state].second
}

class SortFilter :
    Filter.Sort(
        "Sort by",
        arrayOf("Views", "Name", "Updated", "Added", "Chapters", "Followers"),
        Selection(2, false), // Default: Updated, descending
    ) {
    val selected: String
        get() = when (state?.index) {
            0 -> "1"
            1 -> "2"
            2 -> "3"
            3 -> "4"
            4 -> "5"
            5 -> "6"
            else -> "3"
        }
}

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            "All" to "",
            "Webtoon" to "1",
            "Manhwa" to "2",
            "Manhua" to "3",
            "Manga" to "4",
            "Manhwa +19" to "5",
            "+19 Uncensored" to "6",
            "BL Uncensored" to "7",
            "Manhwa BL" to "8",
            "Novel" to "9",
            "Manhua BL" to "10",
            "Visual novel" to "11",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "1",
            "Paused" to "2",
            "Abandoned" to "3",
            "Completed" to "4",
            "Cancelled" to "5",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        arrayOf(
            "All" to "",
            "Action" to "1",
            "Adventure" to "2",
            "Animation" to "3",
            "Apocalyptic" to "4",
            "Boys Love" to "5",
            "Comedy" to "6",
            "Crime" to "7",
            "Cyberpunk" to "8",
            "Demons" to "9",
            "Drama" to "10",
            "Ecchi" to "11",
            "Family" to "12",
            "Fantasy" to "13",
            "Foreign" to "14",
            "Gender Bender" to "15",
            "Girls Love" to "16",
            "Gore" to "17",
            "Harem" to "18",
            "History" to "19",
            "Horror" to "20",
            "Kids" to "21",
            "Magic" to "22",
            "Martial Arts" to "23",
            "Mecha" to "24",
            "Military" to "25",
            "Mystery" to "26",
            "Music" to "27",
            "Parody" to "28",
            "Police" to "29",
            "Psychological" to "30",
            "Reality" to "31",
            "Reincarnation" to "32",
            "Romance" to "33",
            "Samurai" to "34",
            "School Life" to "35",
            "Sci-Fi" to "36",
            "Slice of Life" to "37",
            "Soap Opera" to "38",
            "Sports" to "39",
            "Supernatural" to "40",
            "Super Power" to "41",
            "Survival" to "42",
            "Thriller" to "43",
            "Tragedy" to "44",
            "Vampires" to "45",
            "Virtual Reality" to "46",
            "War" to "47",
            "Western" to "48",
            "Dungeon" to "49",
            "Systems" to "50",
            "Revenge" to "51",
            "Regression" to "52",
            "Isekai" to "53",
            "Video Games" to "54",
            "Villainess" to "55",
            "Adult" to "56",
            "Smut" to "57",
            "Transmigration" to "58",
            "Ghosts" to "59",
            "Dragons" to "60",
            "Beasts" to "61",
            "Aliens" to "62",
            "Omegaverse" to "63",
        ),
    )
