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

class SortFilter :
    SelectFilter(
        "Sort",
        "sort",
        arrayOf(
            "Default" to "title",
            "Latest Update" to "latest_update",
            "Popularity" to "popular",
            "Rating" to "rating",
        ),
    )

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
            "4-Koma" to "4-koma",
            "Action" to "action",
            "Adult" to "adult",
            "Adventure" to "adventure",
            "Aliens" to "aliens",
            "Animals" to "animals",
            "Anthology" to "anthology",
            "Comedy" to "comedy",
            "Cooking" to "cooking",
            "Crime" to "crime",
            "Crossdressing" to "crossdressing",
            "Delinquents" to "delinquents",
            "Demon" to "demon",
            "Demons" to "demons",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Game" to "game",
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
            "Magical Girls" to "magical-girls",
            "Martial Art" to "martial-art",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Mecha" to "mecha",
            "Medical" to "medical",
            "Military" to "military",
            "Monster" to "monster",
            "Monster Girls" to "monster-girls",
            "Monsters" to "monsters",
            "Music" to "music",
            "Mystery" to "mystery",
            "Ninja" to "ninja",
            "Office Workers" to "office-workers",
            "Oneshot" to "oneshot",
            "Philosophical" to "philosophical",
            "Police" to "police",
            "Psychological" to "psychological",
            "Regression" to "regression",
            "Reincarnation" to "reincarnation",
            "Reverse Harem" to "reverse-harem",
            "Romance" to "romance",
            "Samurai" to "samurai",
            "School" to "school",
            "School Life" to "school-life",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Sexual Violence" to "sexual-violence",
            "Shotacon" to "shotacon",
            "Shoujo" to "shoujo",
            "Shoujo Ai" to "shoujo-ai",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Smut" to "smut",
            "Sports" to "sports",
            "Super Power" to "super-power",
            "Supernatural" to "supernatural",
            "Survival" to "survival",
            "Suspense" to "suspense",
            "System" to "system",
            "Thriller" to "thriller",
            "Time Travel" to "time-travel",
            "Tragedy" to "tragedy",
            "Urban" to "urban",
            "Vampire" to "vampire",
            "Video Games" to "video-games",
            "Villainess" to "villainess",
            "Virtual Reality" to "virtual-reality",
            "Web Comic" to "web-comic",
            "Webtoons" to "webtoons",
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
