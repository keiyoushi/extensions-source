package eu.kanade.tachiyomi.extension.en.mangasect

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

class ChapterCountFilter : SelectFilter("Chapter count", chapterCount) {
    companion object {
        private val chapterCount = listOf(
            Pair(">= 0", "0"),
            Pair(">= 10", "10"),
            Pair(">= 30", "30"),
            Pair(">= 50", "50"),
            Pair(">= 100", "100"),
            Pair(">= 200", "200"),
            Pair(">= 300", "300"),
            Pair(">= 400", "400"),
            Pair(">= 500", "500"),
        )
    }
}

class GenderFilter : SelectFilter("Manga Gender", gender) {
    companion object {
        private val gender = listOf(
            Pair("All", "All"),
            Pair("Boy", "Boy"),
            Pair("Girl", "Girl"),
        )
    }
}

class StatusFilter : SelectFilter("Status", status) {
    companion object {
        private val status = listOf(
            Pair("All", ""),
            Pair("Completed", "completed"),
            Pair("OnGoing", "on-going"),
            Pair("On-Hold", "on-hold"),
            Pair("Canceled", "canceled"),
        )
    }
}

class SortFilter : SelectFilter("Sort", sort) {
    companion object {
        private val sort = listOf(
            Pair("Default", "default"),
            Pair("Latest Updated", "latest-updated"),
            Pair("Most Viewed", "most-viewd"),
            Pair("Score", "score"),
            Pair("Name A-Z", "az"),
            Pair("Name Z-A", "za"),
            Pair("Newest", "new"),
            Pair("Oldest", "old"),
        )
    }
}

class GenreFilter : Filter.Group<CheckBoxFilter>(
    "Genre",
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }

    companion object {
        private val genres = listOf(
            Pair("Action", "29"),
            Pair("Adaptation", "66"),
            Pair("Adult", "108"),
            Pair("Adventure", "33"),
            Pair("Aliens", "2326"),
            Pair("Animals", "199"),
            Pair("Comedy", "35"),
            Pair("Comic", "109"),
            Pair("Cooking", "26"),
            Pair("Crime", "274"),
            Pair("Delinquents", "234"),
            Pair("Demons", "136"),
            Pair("Drama", "39"),
            Pair("Dungeons", "204"),
            Pair("Ecchi", "54"),
            Pair("Fantasy", "30"),
            Pair("Full Color", "27"),
            Pair("Genderswap", "1441"),
            Pair("Genius MC", "209"),
            Pair("Ghosts", "1527"),
            Pair("Gore", "1678"),
            Pair("Harem", "43"),
            Pair("Historical", "49"),
            Pair("Horror", "69"),
            Pair("Incest", "1189"),
            Pair("Isekai", "40"),
            Pair("Loli", "198"),
            Pair("Long Strip", "233"),
            Pair("Magic", "212"),
            Pair("Magical Girls", "1676"),
            Pair("Manhua", "58"),
            Pair("Manhwa", "80"),
            Pair("Martial Arts", "32"),
            Pair("Mature", "34"),
            Pair("Mecha", "70"),
            Pair("Medical", "2113"),
            Pair("Military", "1531"),
            Pair("Monster", "218"),
            Pair("Monster Girls", "201"),
            Pair("Monsters", "63"),
            Pair("Murim", "208"),
            Pair("Music", "412"),
            Pair("Mystery", "31"),
            Pair("One shot", "155"),
            Pair("Overpowered", "206"),
            Pair("Police", "275"),
            Pair("Post-Apocalyptic", "197"),
            Pair("Psychological", "36"),
            Pair("Rebirth", "1435"),
            Pair("Recarnation", "67"),
            Pair("Regression", "205"),
            Pair("Reincarnation", "64"),
            Pair("Return", "1454"),
            Pair("Returner", "211"),
            Pair("Revenge", "219"),
            Pair("Romance", "37"),
            Pair("School Life", "44"),
            Pair("Sci fi", "42"),
            Pair("Sci-fi", "216"),
            Pair("Seinen", "52"),
            Pair("Sexual Violence", "2325"),
            Pair("Shota", "2327"),
            Pair("Shoujo", "92"),
            Pair("Shounen", "38"),
            Pair("Shounen ai", "103"),
            Pair("Slice of Life", "68"),
            Pair("Super power", "213"),
            Pair("Superhero", "1630"),
            Pair("Supernatural", "41"),
            Pair("Survival", "463"),
            Pair("System", "203"),
            Pair("Thriller", "462"),
            Pair("Time travel", "65"),
            Pair("tower", "207"),
            Pair("Tragedy", "51"),
            Pair("Transmigration", "217"),
            Pair("Uncategorized", "55"),
            Pair("Vampires", "200"),
            Pair("Video Games", "1606"),
            Pair("Virtual Reality", "757"),
            Pair("Web comic", "98"),
            Pair("Webtoons", "77"),
            Pair("Wuxia", "202"),
            Pair("Zombies", "464"),
        )
    }
}
