package eu.kanade.tachiyomi.extension.en.silentquill

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            "All" to "",
            "Comedy" to "comedy",
            "Fantasy" to "fantasy",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Shounen" to "shounen",
            "Harem" to "harem",
            "Ecchi" to "ecchi",
            "Action" to "action",
            "Seinen" to "seinen",
            "Adventure" to "adventure",
            "Drama" to "drama",
            "Completed" to "completed",
            "Adult" to "adult",
            "Slice of Life" to "slice-of-life",
            "Erotica" to "erotica",
            "isekai" to "isekai",
            "Mature" to "mature",
            "Supernatural" to "supernatural",
            "Mystery" to "mystery",
            "Psychological" to "psychological",
            "Sexual Violence" to "sexual-violence",
            "Demons" to "demons",
            "Magic" to "magic",
            "Sci-fi" to "sci-fi",
            "Adaptation" to "adaptation",
            "Gender Bender" to "gender-bender",
            "Gyaru" to "gyaru",
            "Monsters" to "monsters",
            "Reincarnation" to "reincarnation",
            "Sports" to "sports",
            "Tragedy" to "tragedy",
            "Web Comic" to "web-comic",
            "Ghosts" to "ghosts",
            "Gore" to "gore",
            "Horror" to "horror",
            "Josei" to "josei",
            "Monster Girls" to "monster-girls",
            "One-shot" to "one-shot",
            "Shoujo" to "shoujo",
            "Survival" to "survival",
            "Zombies" to "zombies",
            "Aliens" to "aliens",
            "Delinquents" to "delinquents",
            "Full Color" to "full-color",
            "Genderswap" to "genderswap",
            "Girls' Love" to "girls-love",
            "Hentai" to "hentai",
            "Historical" to "historical",
            "Martial Arts" to "martial-arts",
            "Mecha" to "mecha",
            "Myster" to "myster",
            "Smut" to "smut",
            "Suggestive" to "suggestive",
            "Thriller" to "thriller",
            "Video Games" to "video-games",
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
