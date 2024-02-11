package eu.kanade.tachiyomi.extension.all.mangareaderto

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriFilter
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriMultiSelectFilter
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriPartFilter
import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl
import java.util.Calendar

class TypeFilter : UriPartFilter(
    "Type",
    "type",
    arrayOf(
        Pair("All", ""),
        Pair("Manga", "1"),
        Pair("One-Shot", "2"),
        Pair("Doujinshi", "3"),
        Pair("Light Novel", "4"),
        Pair("Manhwa", "5"),
        Pair("Manhua", "6"),
        Pair("Comic", "7"),
    ),
)

class StatusFilter : UriPartFilter(
    "Status",
    "status",
    arrayOf(
        Pair("All", ""),
        Pair("Finished", "1"),
        Pair("Publishing", "2"),
        Pair("On Hiatus", "3"),
        Pair("Discontinued", "4"),
        Pair("Not yet published", "5"),
    ),
)

class RatingFilter : UriPartFilter(
    "Rating Type",
    "rating_type",
    arrayOf(
        Pair("All", ""),
        Pair("G - All Ages", "1"),
        Pair("PG - Children", "2"),
        Pair("PG-13 - Teens 13 or older", "3"),
        Pair("R - 17+ (violence & profanity)", "4"),
        Pair("R+ - Mild Nudity", "5"),
        Pair("Rx - Hentai", "6"),
    ),
)

class ScoreFilter : UriPartFilter(
    "Score",
    "score",
    arrayOf(
        Pair("All", ""),
        Pair("(1) Appalling", "1"),
        Pair("(2) Horrible", "2"),
        Pair("(3) Very Bad", "3"),
        Pair("(4) Bad", "4"),
        Pair("(5) Average", "5"),
        Pair("(6) Fine", "6"),
        Pair("(7) Good", "7"),
        Pair("(8) Very Good", "8"),
        Pair("(9) Great", "9"),
        Pair("(10) Masterpiece", "10"),
    ),
)

class YearFilter(name: String, param: String) : UriPartFilter(
    name,
    param,
    years,
) {
    companion object {
        private val nextYear by lazy {
            Calendar.getInstance()[Calendar.YEAR] + 1
        }

        private val years = Array(nextYear - 1916) { year ->
            if (year == 0) {
                Pair("Any", "")
            } else {
                (nextYear - year).toString().let { Pair(it, it) }
            }
        }
    }
}

class MonthFilter(name: String, param: String) : UriPartFilter(
    name,
    param,
    months,
) {
    companion object {
        private val months = Array(13) { months ->
            if (months == 0) {
                Pair("Any", "")
            } else {
                Pair("%02d".format(months), months.toString())
            }
        }
    }
}

class DayFilter(name: String, param: String) : UriPartFilter(
    name,
    param,
    days,
) {
    companion object {
        private val days = Array(32) { day ->
            if (day == 0) {
                Pair("Any", "")
            } else {
                Pair("%02d".format(day), day.toString())
            }
        }
    }
}

sealed class DateFilter(
    type: String,
    private val values: List<UriPartFilter>,
) : Filter.Group<UriPartFilter>("$type Date", values), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        values.forEach {
            it.addToUri(builder)
        }
    }
}

class StartDateFilter(
    values: List<UriPartFilter> = parts,
) : DateFilter("Start", values) {
    companion object {
        private val parts = listOf(
            YearFilter("Year", "sy"),
            MonthFilter("Month", "sm"),
            DayFilter("Day", "sd"),
        )
    }
}

class EndDateFilter(
    values: List<UriPartFilter> = parts,
) : DateFilter("End", values) {
    companion object {
        private val parts = listOf(
            YearFilter("Year", "ey"),
            MonthFilter("Month", "em"),
            DayFilter("Day", "ed"),
        )
    }
}

class GenreFilter : UriMultiSelectFilter(
    "Genres",
    "genres",
    arrayOf(
        Pair("Action", "1"),
        Pair("Adventure", "2"),
        Pair("Cars", "3"),
        Pair("Comedy", "4"),
        Pair("Dementia", "5"),
        Pair("Demons", "6"),
        Pair("Doujinshi", "7"),
        Pair("Drama", "8"),
        Pair("Ecchi", "9"),
        Pair("Fantasy", "10"),
        Pair("Game", "11"),
        Pair("Gender Bender", "12"),
        Pair("Harem", "13"),
        Pair("Hentai", "14"),
        Pair("Historical", "15"),
        Pair("Horror", "16"),
        Pair("Josei", "17"),
        Pair("Kids", "18"),
        Pair("Magic", "19"),
        Pair("Martial Arts", "20"),
        Pair("Mecha", "21"),
        Pair("Military", "22"),
        Pair("Music", "23"),
        Pair("Mystery", "24"),
        Pair("Parody", "25"),
        Pair("Police", "26"),
        Pair("Psychological", "27"),
        Pair("Romance", "28"),
        Pair("Samurai", "29"),
        Pair("School", "30"),
        Pair("Sci-Fi", "31"),
        Pair("Seinen", "32"),
        Pair("Shoujo", "33"),
        Pair("Shoujo Ai", "34"),
        Pair("Shounen", "35"),
        Pair("Shounen Ai", "36"),
        Pair("Slice of Life", "37"),
        Pair("Space", "38"),
        Pair("Sports", "39"),
        Pair("Super Power", "40"),
        Pair("Supernatural", "41"),
        Pair("Thriller", "42"),
        Pair("Vampire", "43"),
        Pair("Yaoi", "44"),
        Pair("Yuri", "45"),
    ),
    ",",
)
