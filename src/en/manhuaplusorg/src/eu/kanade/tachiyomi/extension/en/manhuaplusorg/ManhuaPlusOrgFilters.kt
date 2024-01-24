package eu.kanade.tachiyomi.extension.en.manhuaplusorg

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
            Pair("Most Viewed", "views"),
            Pair("Most Viewed Month", "views_month"),
            Pair("Most Viewed Week", "views_week"),
            Pair("Most Viewed Day", "views_day"),
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
            Pair("Adaptation", "87"),
            Pair("Adult", "31"),
            Pair("Adventure", "5"),
            Pair("Animals", "1657"),
            Pair("Cartoon", "46"),
            Pair("Comedy", "14"),
            Pair("Demons", "284"),
            Pair("Drama", "59"),
            Pair("Ecchi", "67"),
            Pair("Fantasy", "6"),
            Pair("Full Color", "89"),
            Pair("Genderswap", "2409"),
            Pair("Ghosts", "2253"),
            Pair("Gore", "1182"),
            Pair("Harem", "17"),
            Pair("Historical", "642"),
            Pair("Horror", "797"),
            Pair("Isekai", "239"),
            Pair("Live action", "11"),
            Pair("Long Strip", "86"),
            Pair("Magic", "90"),
            Pair("Magical Girls", "1470"),
            Pair("Manhua", "7"),
            Pair("Manhwa", "70"),
            Pair("Martial Arts", "8"),
            Pair("Mature", "12"),
            Pair("Mecha", "786"),
            Pair("Medical", "1443"),
            Pair("Monsters", "138"),
            Pair("Mystery", "9"),
            Pair("Post-Apocalyptic", "285"),
            Pair("Psychological", "798"),
            Pair("Reincarnation", "139"),
            Pair("Romance", "987"),
            Pair("School Life", "10"),
            Pair("Sci-fi", "135"),
            Pair("Seinen", "196"),
            Pair("Shounen", "26"),
            Pair("Shounen ai", "64"),
            Pair("Slice of Life", "197"),
            Pair("Superhero", "136"),
            Pair("Supernatural", "13"),
            Pair("Survival", "140"),
            Pair("Thriller", "137"),
            Pair("Time travel", "231"),
            Pair("Tragedy", "15"),
            Pair("Video Games", "283"),
            Pair("Villainess", "676"),
            Pair("Virtual Reality", "611"),
            Pair("Web comic", "88"),
            Pair("Webtoon", "18"),
            Pair("Wuxia", "239"),
        )
    }
}
