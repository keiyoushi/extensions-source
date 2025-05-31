package eu.kanade.tachiyomi.extension.en.manganow

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriMultiSelectFilter
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriPartFilter
import java.util.Calendar

class TypeFilter : UriPartFilter(
    "Type",
    "type",
    arrayOf(
        Pair("All", ""),
        Pair("Manga", "8"),
        Pair("Manhua", "53"),
        Pair("Manhwa", "39"),
        Pair("OEL", "169"),
        Pair("Web Comic", "344"),
        Pair("Webtoon", "983"),
    ),
)

class StatusFilter : UriPartFilter(
    "Status",
    "status",
    arrayOf(
        Pair("All", ""),
        Pair("Completed", "completed"),
        Pair("Ongoing", "ongoing"),
        Pair("On-hiatus", "on-hiatus"),
        Pair("Discontinued", "discontinued"),
        Pair("Not-yet-published", "not-yet-published"),
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

class YearFilter : UriPartFilter(
    "Release Year",
    "sy",
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

class GenreFilter : UriMultiSelectFilter(
    "Genres",
    "genres",
    arrayOf(
        Pair("Action", "1"),
        Pair("Adventure", "2"),
        Pair("Animated", "641"),
        Pair("Anime", "375"),
        Pair("Cartoon", "463"),
        Pair("Comedy", "3"),
        Pair("Comic", "200"),
        Pair("Completed", "326"),
        Pair("Cooking", "133"),
        Pair("Detective", "386"),
        Pair("Doujinshi", "534"),
        Pair("Drama", "10"),
        Pair("Ecchi", "41"),
        Pair("Fantasy", "17"),
        Pair("Gender Bender", "89"),
        Pair("Harem", "11"),
        Pair("Historical", "30"),
        Pair("Horror", "21"),
        Pair("Isekai", "70"),
        Pair("Josei", "67"),
        Pair("Magic", "420"),
        Pair("Manga", "137"),
        Pair("Manhua", "51"),
        Pair("Manhwa", "79"),
        Pair("Martial Arts", "12"),
        Pair("Mature", "22"),
        Pair("Mecha", "72"),
        Pair("Military", "1180"),
        Pair("Mystery", "44"),
        Pair("One shot", "721"),
        Pair("Psychological", "23"),
        Pair("Reincarnation", "1603"),
        Pair("Romance", "13"),
        Pair("School Life", "4"),
        Pair("Sci-fi", "24"),
        Pair("Seinen", "25"),
        Pair("Shoujo", "33"),
        Pair("Shoujo Ai", "123"),
        Pair("Shounen", "5"),
        Pair("Shounen Ai", "680"),
        Pair("Slice of Life", "14"),
        Pair("Smut", "734"),
        Pair("Sports", "142"),
        Pair("Super Power", "28"),
        Pair("Supernatural", "6"),
        Pair("Thriller", "1816"),
        Pair("Tragedy", "97"),
        Pair("Webtoon", "60"),
    ),
    join = ",",
)
