package eu.kanade.tachiyomi.extension.id.themanga

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val queryName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = vals[state].second
        if (value.isNotBlank()) {
            url.addQueryParameter(queryName, value)
        }
    }
}

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        "genre",
        arrayOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Aliens", "aliens"),
            Pair("Animals", "animals"),
            Pair("Comedy", "comedy"),
            Pair("Cooking", "cooking"),
            Pair("Crossdressing", "crossdressing"),
            Pair("Delinquents", "delinquents"),
            Pair("Demons", "demons"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Genderswap", "genderswap"),
            Pair("Ghosts", "ghosts"),
            Pair("Gore", "gore"),
            Pair("Gyaru", "gyaru"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Incest", "incest"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Loli", "loli"),
            Pair("Mafia", "mafia"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Military", "military"),
            Pair("Monster Girls", "monster-girls"),
            Pair("Monsters", "monsters"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Ninja", "ninja"),
            Pair("Office Workers", "office-workers"),
            Pair("Police", "police"),
            Pair("Psychological", "psychological"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("Samurai", "samurai"),
            Pair("School", "school"),
            Pair("School Life", "school-life"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Sexual Violence", "sexual-violence"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Supernatural", "supernatural"),
            Pair("Survival", "survival"),
            Pair("Time Travel", "time-travel"),
            Pair("Tragedy", "tragedy"),
            Pair("Vampire", "vampire"),
            Pair("Video Games", "video-games"),
            Pair("Villainess", "villainess"),
            Pair("Virtual Reality", "virtual-reality"),
            Pair("Web Comic", "web-comic"),
            Pair("Yuri", "yuri"),
            Pair("Zombies", "zombies"),
        ),
    )

class TextFilter(name: String, private val queryName: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        if (state.isNotBlank()) {
            url.addQueryParameter(queryName, state)
        }
    }
}

class OtherFilterGroup :
    Filter.Group<TextFilter>(
        "Other filters",
        listOf(
            TextFilter("Minimum Rating", "rating_min"),
            TextFilter("Year", "year"),
            TextFilter("Author", "author"),
            TextFilter("Artist", "artist"),
            TextFilter("Type", "type"),
        ),
    ),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.forEach { it.addToUrl(url) }
    }
}
