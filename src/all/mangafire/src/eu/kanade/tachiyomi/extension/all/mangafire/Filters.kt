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

class TypeFilter :
    UriMultiSelectFilter(
        "Type",
        "types[]",
        arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Other", "other"),
        ),
    )

class GenreFilter :
    UriTriSelectFilter(
        "Genres",
        "genres_in[]",
        "genres_ex[]",
        arrayOf(
            Pair("Action", "1"),
            Pair("Adult", "268929"),
            Pair("Adventure", "78"),
            Pair("Avant Garde", "3"),
            Pair("Boys Love", "4"),
            Pair("Comedy", "5"),
            Pair("Crime", "268921"),
            Pair("Demons", "77"),
            Pair("Drama", "6"),
            Pair("Ecchi", "7"),
            Pair("Fantasy", "79"),
            Pair("Girls Love", "9"),
            Pair("Gourmet", "10"),
            Pair("Harem", "11"),
            Pair("Hentai", "268930"),
            Pair("Historical", "268922"),
            Pair("Horror", "530"),
            Pair("Isekai", "13"),
            Pair("Iyashikei", "531"),
            Pair("Josei", "15"),
            Pair("Kids", "532"),
            Pair("Magic", "539"),
            Pair("Magical Girls", "268923"),
            Pair("Mahou Shoujo", "533"),
            Pair("Martial Arts", "534"),
            Pair("Mature", "268931"),
            Pair("Mecha", "19"),
            Pair("Medical", "268924"),
            Pair("Military", "535"),
            Pair("Music", "21"),
            Pair("Mystery", "22"),
            Pair("Parody", "23"),
            Pair("Philosophical", "268925"),
            Pair("Psychological", "536"),
            Pair("Reverse Harem", "25"),
            Pair("Romance", "26"),
            Pair("School", "73"),
            Pair("Sci-Fi", "28"),
            Pair("Seinen", "537"),
            Pair("Shoujo", "30"),
            Pair("Shounen", "31"),
            Pair("Slice of Life", "538"),
            Pair("Smut", "268932"),
            Pair("Space", "33"),
            Pair("Sports", "34"),
            Pair("Super Power", "75"),
            Pair("Superhero", "268926"),
            Pair("Supernatural", "76"),
            Pair("Suspense", "37"),
            Pair("Thriller", "38"),
            Pair("Tragedy", "268927"),
            Pair("Vampire", "39"),
            Pair("Wuxia", "268928"),
        ),
    )

class ThemeFilter :
    UriMultiSelectFilter(
        "Themes",
        "theme_ids[]",
        arrayOf(
            Pair("Aliens", "268933"),
            Pair("Animals", "268934"),
            Pair("Cooking", "268935"),
            Pair("Crossdressing", "268936"),
            Pair("Delinquents", "268937"),
            Pair("Demons", "268938"),
            Pair("Genderswap", "268939"),
            Pair("Ghosts", "268940"),
            Pair("Gyaru", "268941"),
            Pair("Harem", "268942"),
            Pair("Incest", "268943"),
            Pair("Loli", "268944"),
            Pair("Mafia", "268945"),
            Pair("Magic", "268946"),
            Pair("Martial Arts", "268947"),
            Pair("Military", "268948"),
            Pair("Monster Girls", "268949"),
            Pair("Monsters", "268950"),
            Pair("Music", "268951"),
            Pair("Ninja", "268952"),
            Pair("Office Workers", "268953"),
            Pair("Police", "268954"),
            Pair("Post-Apocalyptic", "268955"),
            Pair("Reincarnation", "268956"),
            Pair("Reverse Harem", "268957"),
            Pair("Samurai", "268958"),
            Pair("School Life", "268959"),
            Pair("Shota", "268960"),
            Pair("Supernatural", "268961"),
            Pair("Survival", "268962"),
            Pair("Time Travel", "268963"),
            Pair("Traditional Games", "268964"),
            Pair("Vampires", "268965"),
            Pair("Video Games", "268966"),
            Pair("Villainess", "268967"),
            Pair("Virtual Reality", "268968"),
            Pair("Zombies", "268969"),
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
            Pair("Releasing", "releasing"),
            Pair("Finished", "finished"),
            Pair("On Hiatus", "on_hiatus"),
            Pair("Discontinued", "discontinued"),
            Pair("Not Yet Released", "not_yet_released"),
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
