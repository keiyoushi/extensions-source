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
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        "genre",
        arrayOf(
            "All" to "",
            "Action" to "action",
            "Adventure" to "adventure",
            "Aliens" to "aliens",
            "Animals" to "animals",
            "Comedy" to "comedy",
            "Cooking" to "cooking",
            "Crossdressing" to "crossdressing",
            "Delinquents" to "delinquents",
            "Demons" to "demons",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Gender Bender" to "gender-bender",
            "Genderswap" to "genderswap",
            "Ghosts" to "ghosts",
            "Gore" to "gore",
            "Gyaru" to "gyaru",
            "Harem" to "harem",
            "Historical" to "historical",
            "Horror" to "horror",
            "Incest" to "incest",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Loli" to "loli",
            "Mafia" to "mafia",
            "Magic" to "magic",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Military" to "military",
            "Monster Girls" to "monster-girls",
            "Monsters" to "monsters",
            "Music" to "music",
            "Mystery" to "mystery",
            "Ninja" to "ninja",
            "Office Workers" to "office-workers",
            "Police" to "police",
            "Psychological" to "psychological",
            "Reincarnation" to "reincarnation",
            "Romance" to "romance",
            "Samurai" to "samurai",
            "School" to "school",
            "School Life" to "school-life",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Sexual Violence" to "sexual-violence",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Supernatural" to "supernatural",
            "Survival" to "survival",
            "Time Travel" to "time-travel",
            "Tragedy" to "tragedy",
            "Vampire" to "vampire",
            "Video Games" to "video-games",
            "Villainess" to "villainess",
            "Virtual Reality" to "virtual-reality",
            "Web Comic" to "web-comic",
            "Yuri" to "yuri",
            "Zombies" to "zombies",
        ),
    )

class TextFilter(
    name: String,
    private val queryName: String,
) : Filter.Text(name),
    UrlFilter {

    override fun addToUrl(url: HttpUrl.Builder) {
        state.takeIf { it.isNotBlank() }
            ?.let { url.addQueryParameter(queryName, it) }
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
