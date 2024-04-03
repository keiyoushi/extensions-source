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
            Pair("Action", "4"),
            Pair("Adaptation", "180"),
            Pair("Adventure", "14"),
            Pair("Aliens", "181"),
            Pair("Animals", "43"),
            Pair("Comedy", "15"),
            Pair("Delinquents", "172"),
            Pair("Demons", "44"),
            Pair("Drama", "42"),
            Pair("Ecchi", "95"),
            Pair("Fantasy", "5"),
            Pair("Full Color", "9"),
            Pair("Genderswap", "70"),
            Pair("Ghosts", "226"),
            Pair("Harem", "96"),
            Pair("Historical", "16"),
            Pair("Horror", "99"),
            Pair("Isekai", "20"),
            Pair("Loli", "200"),
            Pair("Long Strip", "7"),
            Pair("Magic", "201"),
            Pair("Martial Arts", "6"),
            Pair("Mecha", "2848"),
            Pair("Medical", "1512"),
            Pair("Military", "638"),
            Pair("Monster Girls", "3152"),
            Pair("Monsters", "45"),
            Pair("Mystery", "752"),
            Pair("Office Workers", "1765"),
            Pair("Official Colored", "3041"),
            Pair("Philosophical", "2083"),
            Pair("Post-Apocalyptic", "1479"),
            Pair("Psychological", "17"),
            Pair("Reincarnation", "21"),
            Pair("Romance", "18"),
            Pair("School Life", "83"),
            Pair("Sci-Fi", "84"),
            Pair("Seinen", "4253"),
            Pair("Sexual Violence", "255"),
            Pair("Slice of Life", "171"),
            Pair("Superhero", "871"),
            Pair("Supernatural", "19"),
            Pair("Survival", "202"),
            Pair("Thriller", "1478"),
            Pair("Time Travel", "771"),
            Pair("Tragedy", "1420"),
            Pair("Video Games", "116"),
            Pair("Villainess", "1555"),
            Pair("Virtual Reality", "2828"),
            Pair("Web Comic", "8"),
            Pair("Wuxia", "10"),
            Pair("Zombies", "1480"),
        )
    }
}
