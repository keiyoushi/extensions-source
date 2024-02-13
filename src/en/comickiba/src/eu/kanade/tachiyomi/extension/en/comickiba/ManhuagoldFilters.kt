package eu.kanade.tachiyomi.extension.en.comickiba

import eu.kanade.tachiyomi.source.model.Filter

object Note : Filter.Header("NOTE: Ignored if using text search!")

sealed class Select(
    name: String,
    val param: String,
    values: Array<String>,
) : Filter.Select<String>(name, values) {
    open val selection: String
        get() = if (state == 0) "" else state.toString()
}

class StatusFilter(
    values: Array<String> = statuses.keys.toTypedArray(),
) : Select("Status", "status", values) {
    override val selection: String
        get() = statuses[values[state]]!!

    companion object {
        private val statuses = mapOf(
            "All" to "",
            "Completed" to "completed",
            "OnGoing" to "on-going",
            "On-Hold" to "on-hold",
            "Canceled" to "canceled",
        )
    }
}

class SortFilter(
    values: Array<String> = orders.keys.toTypedArray(),
) : Select("Sort", "sort", values) {
    override val selection: String
        get() = orders[values[state]]!!

    companion object {
        private val orders = mapOf(
            "Default" to "default",
            "Latest Updated" to "latest-updated",
            "Most Viewed" to "views",
            "Most Viewed Month" to "views_month",
            "Most Viewed Week" to "views_week",
            "Most Viewed Day" to "views_day",
            "Score" to "score",
            "Name A-Z" to "az",
            "Name Z-A" to "za",
            "The highest chapter count" to "chapters",
            "Newest" to "new",
            "Oldest" to "old",
        )
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenresFilter(
    values: List<Genre> = genres,
) : Filter.Group<Genre>("Genres", values) {
    val param = "genres"

    val selection: String
        get() = state.filter { it.state }.joinToString(",") { it.id }

    companion object {
        private val genres: List<Genre>
            get() = listOf(
                Genre("Action", "37"),
                Genre("Adaptation", "19"),
                Genre("Adult", "5310"),
                Genre("Adventure", "38"),
                Genre("Aliens", "5436"),
                Genre("Animals", "1552"),
                Genre("Award Winning", "39"),
                Genre("Comedy", "202"),
                Genre("Comic", "287"),
                Genre("Cooking", "277"),
                Genre("Crime", "2723"),
                Genre("Delinquents", "4438"),
                Genre("Demons", "379"),
                Genre("Drama", "3"),
                Genre("Ecchi", "17"),
                Genre("Fantasy", "197"),
                Genre("Full Color", "13"),
                Genre("Gender Bender", "221"),
                Genre("Genderswap", "2290"),
                Genre("Ghosts", "2866"),
                Genre("Gore", "42"),
                Genre("Harem", "222"),
                Genre("Historical", "4"),
                Genre("Horror", "5"),
                Genre("Isekai", "259"),
                Genre("Josei", "292"),
                Genre("Loli", "5449"),
                Genre("Long Strip", "7"),
                Genre("Magic", "272"),
                Genre("Manhwa", "266"),
                Genre("Martial Arts", "40"),
                Genre("Mature", "5311"),
                Genre("Mecha", "2830"),
                Genre("Medical", "1598"),
                Genre("Military", "43"),
                Genre("Monster Girls", "2307"),
                Genre("Monsters", "298"),
                Genre("Music", "3182"),
                Genre("Mystery", "6"),
                Genre("Office Workers", "14"),
                Genre("Official Colored", "1046"),
                Genre("Philosophical", "2776"),
                Genre("Post-Apocalyptic", "1059"),
                Genre("Psychological", "493"),
                Genre("Reincarnation", "204"),
                Genre("Reverse", "280"),
                Genre("Reverse Harem", "199"),
                Genre("Romance", "186"),
                Genre("School Life", "601"),
                Genre("Sci-Fi", "1845"),
                Genre("Sexual Violence", "731"),
                Genre("Shoujo", "254"),
                Genre("Slice of Life", "10"),
                Genre("Sports", "4066"),
                Genre("Superhero", "481"),
                Genre("Supernatural", "198"),
                Genre("Survival", "44"),
                Genre("Thriller", "1058"),
                Genre("Time Travel", "299"),
                Genre("Tragedy", "41"),
                Genre("Video Games", "1846"),
                Genre("Villainess", "278"),
                Genre("Virtual Reality", "1847"),
                Genre("Web Comic", "12"),
                Genre("Webtoon", "279"),
                Genre("Webtoons", "267"),
                Genre("Wuxia", "203"),
                Genre("Yaoi", "18"),
                Genre("Yuri", "11"),
                Genre("Zombies", "1060"),
            )
    }
}
