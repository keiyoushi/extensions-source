package eu.kanade.tachiyomi.extension.all.mangareaderto

import eu.kanade.tachiyomi.source.model.Filter
import java.util.Calendar

object Note : Filter.Header("NOTE: Ignored if using text search!")

sealed class Select(
    name: String,
    val param: String,
    values: Array<String>,
) : Filter.Select<String>(name, values) {
    open val selection: String
        get() = if (state == 0) "" else state.toString()
}

class TypeFilter(
    values: Array<String> = types,
) : Select("Type", "type", values) {
    companion object {
        private val types: Array<String>
            get() = arrayOf(
                "All",
                "Manga",
                "One-Shot",
                "Doujinshi",
                "Light Novel",
                "Manhwa",
                "Manhua",
                "Comic",
            )
    }
}

class StatusFilter(
    values: Array<String> = statuses,
) : Select("Status", "status", values) {
    companion object {
        private val statuses: Array<String>
            get() = arrayOf(
                "All",
                "Finished",
                "Publishing",
                "On Hiatus",
                "Discontinued",
                "Not yet published",
            )
    }
}

class RatingFilter(
    values: Array<String> = ratings,
) : Select("Rating Type", "rating_type", values) {
    companion object {
        private val ratings: Array<String>
            get() = arrayOf(
                "All",
                "G - All Ages",
                "PG - Children",
                "PG-13 - Teens 13 or older",
                "R - 17+ (violence & profanity)",
                "R+ - Mild Nudity",
                "Rx - Hentai",
            )
    }
}

class ScoreFilter(
    values: Array<String> = scores,
) : Select("Score", "score", values) {
    companion object {
        private val scores: Array<String>
            get() = arrayOf(
                "All",
                "(1) Appalling",
                "(2) Horrible",
                "(3) Very Bad",
                "(4) Bad",
                "(5) Average",
                "(6) Fine",
                "(7) Good",
                "(8) Very Good",
                "(9) Great",
                "(10) Masterpiece",
            )
    }
}

sealed class DateSelect(
    name: String,
    param: String,
    values: Array<String>,
) : Select(name, param, values) {
    override val selection: String
        get() = if (state == 0) "" else values[state]
}

class YearFilter(
    param: String,
    values: Array<String> = years,
) : DateSelect("Year", param, values) {
    companion object {
        private val nextYear by lazy {
            Calendar.getInstance()[Calendar.YEAR] + 1
        }

        private val years: Array<String>
            get() = Array(nextYear - 1916) {
                if (it == 0) "Any" else (nextYear - it).toString()
            }
    }
}

class MonthFilter(
    param: String,
    values: Array<String> = months,
) : DateSelect("Month", param, values) {
    companion object {
        private val months: Array<String>
            get() = Array(13) {
                if (it == 0) "Any" else "%02d".format(it)
            }
    }
}

class DayFilter(
    param: String,
    values: Array<String> = days,
) : DateSelect("Day", param, values) {
    companion object {
        private val days: Array<String>
            get() = Array(32) {
                if (it == 0) "Any" else "%02d".format(it)
            }
    }
}

sealed class DateFilter(
    type: String,
    values: List<DateSelect>,
) : Filter.Group<DateSelect>("$type Date", values)

class StartDateFilter(
    values: List<DateSelect> = parts,
) : DateFilter("Start", values) {
    companion object {
        private val parts: List<DateSelect>
            get() = listOf(
                YearFilter("sy"),
                MonthFilter("sm"),
                DayFilter("sd"),
            )
    }
}

class EndDateFilter(
    values: List<DateSelect> = parts,
) : DateFilter("End", values) {
    companion object {
        private val parts: List<DateSelect>
            get() = listOf(
                YearFilter("ey"),
                MonthFilter("em"),
                DayFilter("ed"),
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
            "Score" to "score",
            "Name A-Z" to "name-az",
            "Release Date" to "release-date",
            "Most Viewed" to "most-viewed",
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
                Genre("Action", "1"),
                Genre("Adventure", "2"),
                Genre("Cars", "3"),
                Genre("Comedy", "4"),
                Genre("Dementia", "5"),
                Genre("Demons", "6"),
                Genre("Doujinshi", "7"),
                Genre("Drama", "8"),
                Genre("Ecchi", "9"),
                Genre("Fantasy", "10"),
                Genre("Game", "11"),
                Genre("Gender Bender", "12"),
                Genre("Harem", "13"),
                Genre("Hentai", "14"),
                Genre("Historical", "15"),
                Genre("Horror", "16"),
                Genre("Josei", "17"),
                Genre("Kids", "18"),
                Genre("Magic", "19"),
                Genre("Martial Arts", "20"),
                Genre("Mecha", "21"),
                Genre("Military", "22"),
                Genre("Music", "23"),
                Genre("Mystery", "24"),
                Genre("Parody", "25"),
                Genre("Police", "26"),
                Genre("Psychological", "27"),
                Genre("Romance", "28"),
                Genre("Samurai", "29"),
                Genre("School", "30"),
                Genre("Sci-Fi", "31"),
                Genre("Seinen", "32"),
                Genre("Shoujo", "33"),
                Genre("Shoujo Ai", "34"),
                Genre("Shounen", "35"),
                Genre("Shounen Ai", "36"),
                Genre("Slice of Life", "37"),
                Genre("Space", "38"),
                Genre("Sports", "39"),
                Genre("Super Power", "40"),
                Genre("Supernatural", "41"),
                Genre("Thriller", "42"),
                Genre("Vampire", "43"),
                Genre("Yaoi", "44"),
                Genre("Yuri", "45"),
            )
    }
}
