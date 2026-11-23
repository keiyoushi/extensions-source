package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    vals: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(name, vals.map { UriMultiSelectOption(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter(param, it.value)
        }
    }
}

class UriTriSelectOption(name: String, val value: String) : Filter.TriState(name)

open class UriTriSelectFilter(
    name: String,
    private val paramIn: String,
    private val paramEx: String,
    vals: Array<Pair<String, String>>,
) : Filter.Group<UriTriSelectOption>(name, vals.map { UriTriSelectOption(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.forEach {
            when (it.state) {
                TriState.STATE_INCLUDE -> builder.addQueryParameter(paramIn, it.value)
                TriState.STATE_EXCLUDE -> builder.addQueryParameter(paramEx, it.value)
                else -> {}
            }
        }
    }
}

val CONTENT_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")

class ContentRatingFilter(
    defaultRatings: Set<String>,
    ratings: List<Pair<String, String>> = CONTENT_RATINGS.map {
        it.replaceFirstChar { c -> c.uppercase() } to it
    },
) : UriMultiSelectFilter(
    "Content Rating",
    "content_rating[]",
    ratings.toTypedArray(),
) {
    init {
        state.forEach { option ->
            if (option.value in defaultRatings) {
                option.state = true
            }
        }
    }
}

class TypeFilter :
    UriMultiSelectFilter(
        "Type",
        "types[]",
        arrayOf(
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Other" to "other",
        ),
    )

class GenreFilter :
    UriTriSelectFilter(
        "Genres",
        "genres_in[]",
        "genres_ex[]",
        arrayOf(
            "Action" to "1",
            "Adult" to "268929",
            "Adventure" to "78",
            "Avant Garde" to "3",
            "Boys Love" to "4",
            "Comedy" to "5",
            "Crime" to "268921",
            "Demons" to "77",
            "Drama" to "6",
            "Ecchi" to "7",
            "Fantasy" to "79",
            "Girls Love" to "9",
            "Gourmet" to "10",
            "Harem" to "11",
            "Hentai" to "268930",
            "Historical" to "268922",
            "Horror" to "530",
            "Isekai" to "13",
            "Iyashikei" to "531",
            "Josei" to "15",
            "Kids" to "532",
            "Magic" to "539",
            "Magical Girls" to "268923",
            "Mahou Shoujo" to "533",
            "Martial Arts" to "534",
            "Mature" to "268931",
            "Mecha" to "19",
            "Medical" to "268924",
            "Military" to "535",
            "Music" to "21",
            "Mystery" to "22",
            "Parody" to "23",
            "Philosophical" to "268925",
            "Psychological" to "536",
            "Reverse Harem" to "25",
            "Romance" to "26",
            "School" to "73",
            "Sci-Fi" to "28",
            "Seinen" to "537",
            "Shoujo" to "30",
            "Shounen" to "31",
            "Slice of Life" to "538",
            "Smut" to "268932",
            "Space" to "33",
            "Sports" to "34",
            "Super Power" to "75",
            "Superhero" to "268926",
            "Supernatural" to "76",
            "Suspense" to "37",
            "Thriller" to "38",
            "Tragedy" to "268927",
            "Vampire" to "39",
            "Wuxia" to "268928",
        ),
    )

class ThemeFilter :
    UriMultiSelectFilter(
        "Themes",
        "theme_ids[]",
        arrayOf(
            "Aliens" to "268933",
            "Animals" to "268934",
            "Cooking" to "268935",
            "Crossdressing" to "268936",
            "Delinquents" to "268937",
            "Demons" to "268938",
            "Genderswap" to "268939",
            "Ghosts" to "268940",
            "Gyaru" to "268941",
            "Harem" to "268942",
            "Incest" to "268943",
            "Loli" to "268944",
            "Mafia" to "268945",
            "Magic" to "268946",
            "Martial Arts" to "268947",
            "Military" to "268948",
            "Monster Girls" to "268949",
            "Monsters" to "268950",
            "Music" to "268951",
            "Ninja" to "268952",
            "Office Workers" to "268953",
            "Police" to "268954",
            "Post-Apocalyptic" to "268955",
            "Reincarnation" to "268956",
            "Reverse Harem" to "268957",
            "Samurai" to "268958",
            "School Life" to "268959",
            "Shota" to "268960",
            "Supernatural" to "268961",
            "Survival" to "268962",
            "Time Travel" to "268963",
            "Traditional Games" to "268964",
            "Vampires" to "268965",
            "Video Games" to "268966",
            "Villainess" to "268967",
            "Virtual Reality" to "268968",
            "Zombies" to "268969",
        ),
    )

class GenreModeFilter :
    Filter.Select<String>("Genre and theme match mode", arrayOf("AND", "OR")),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val mode = if (state == 0) "and" else "or"
        builder.addQueryParameter("genres_mode", mode)
        builder.addQueryParameter("theme_mode", mode)
    }
}

class StatusFilter :
    UriMultiSelectFilter(
        "Status",
        "statuses[]",
        arrayOf(
            "Releasing" to "releasing",
            "Finished" to "finished",
            "On Hiatus" to "on_hiatus",
            "Discontinued" to "discontinued",
            "Not Yet Released" to "not_yet_released",
        ),
    )

class MinChapterFilter :
    Filter.Text("Minimum chapters"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.toIntOrNull()?.takeIf { it > 0 }?.let {
            builder.addQueryParameter("min_chap", it.toString())
        }
    }
}

class YearFromFilter :
    Filter.Text("Release year (From)"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.toIntOrNull()?.let {
            builder.addQueryParameter("year_from", it.toString())
        }
    }
}

class YearToFilter :
    Filter.Text("Release year (To)"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.toIntOrNull()?.let {
            builder.addQueryParameter("year_to", it.toString())
        }
    }
}

class AuthorFilter : Filter.Text("Author / Artist")

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf(
            "Latest update",
            "Best match",
            "Recently added",
            "Title (A–Z)",
            "Title (Z–A)",
            "Year (newest)",
            "Year (oldest)",
            "Highest rated",
            "Most viewed · 7 days",
            "Most viewed · 30 days",
            "Most viewed · all time",
            "Most followed",
        ),
        1,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val sortKey = when (state) {
            0 -> "chapter_updated_at:desc"
            1 -> "relevance:desc"
            2 -> "created_at:desc"
            3 -> "title:asc"
            4 -> "title:desc"
            5 -> "year:desc"
            6 -> "year:asc"
            7 -> "score:desc"
            8 -> "views_7d:desc"
            9 -> "views_30d:desc"
            10 -> "views_total:desc"
            11 -> "follows_total:desc"
            else -> "chapter_updated_at:desc"
        }
        val parts = sortKey.split(":")
        builder.addQueryParameter("order[${parts[0]}]", parts[1])
    }
}
