package eu.kanade.tachiyomi.extension.en.manhuaplus

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectValueFilter(
    name: String,
    values: Array<Pair<String, String>>,
) : Filter.Select<String>(name, values.map { it.first }.toTypedArray()) {
    private val vals = values
    fun toUriPart(): String = vals[state].second
    fun toValue(): String = vals[state].second
}

class GenreFilter :
    SelectValueFilter(
        "Genre",
        arrayOf(
            "All" to "",
            "Action" to "genres/action",
            "Adaptation" to "genres/adaptation",
            "Adult" to "genres/adult",
            "Adventure" to "genres/adventure",
            "Aliens" to "genres/aliens",
            "Animals" to "genres/animals",
            "Award Winning" to "genres/award-winning",
            "Blood" to "genres/blood",
            "Cartoon" to "genres/cartoon",
            "Comedy" to "genres/comedy",
            "Comic" to "genres/comic",
            "Cooking" to "genres/cooking",
            "Crime" to "genres/crime",
            "Delinquents" to "genres/delinquents",
            "Demons" to "genres/demons",
            "Drama" to "genres/drama",
            "Dungeons" to "genres/dungeons",
            "Ecchi" to "genres/ecchi",
            "Fantasy" to "genres/fantasy",
            "Fighting" to "genres/fighting",
            "Full Color" to "genres/full-color",
            "Genderswap" to "genres/genderswap",
            "Ghosts" to "genres/ghosts",
            "Gore" to "genres/gore",
            "Gyaru" to "genres/gyaru",
            "Harem" to "genres/harem",
            "Historical" to "genres/historical",
            "Horror" to "genres/horror",
            "Isekai" to "genres/isekai",
            "Live action" to "genres/live-action",
            "Loli" to "genres/loli",
            "Long Strip" to "genres/long-strip",
            "Mafia" to "genres/mafia",
            "Magical Girls" to "genres/magical-girls",
            "Magic" to "genres/magic",
            "Manhua" to "genres/manhua",
            "Manhwa" to "genres/manhwa",
            "Martial Arts" to "genres/martial-arts",
            "Mature" to "genres/mature",
            "Mecha" to "genres/mecha",
            "Medical" to "genres/medical",
            "Military" to "genres/military",
            "Monster Girls" to "genres/monster-girls",
            "Monsters" to "genres/monsters",
            "Music" to "genres/music",
            "Mystery" to "genres/mystery",
            "Official Colored" to "genres/official-colored",
            "Op-Mc" to "genres/op-mc",
            "Philosophical" to "genres/philosophical",
            "Police" to "genres/police",
            "Post-Apocalyptic" to "genres/post-apocalyptic",
            "Psychological" to "genres/psychological",
            "Reincarnation" to "genres/reincarnation",
            "Returner" to "genres/returner",
            "Revenge" to "genres/revenge",
            "Romance" to "genres/romance",
            "Ruthless Protagonist" to "genres/ruthless-protagonist",
            "School Life" to "genres/school-life",
            "Sci-Fi" to "genres/sci-fi",
            "Seinen" to "genres/seinen",
            "Shounen Ai" to "genres/shounen-ai",
            "Shounen" to "genres/shounen",
            "Slice of life" to "genres/slice-of-life",
            "Smart MC" to "genres/smart-mc",
            "Sports" to "genres/sports",
            "Superhero" to "genres/superhero",
            "Supernatural" to "genres/supernatural",
            "Survival" to "genres/survival",
            "Thriller" to "genres/thriller",
            "Time Travel" to "genres/time-travel",
            "Traditional Games" to "genres/traditional-games",
            "Tragedy" to "genres/tragedy",
            "Vampires" to "genres/vampires",
            "Video Games" to "genres/video-games",
            "Villainess" to "genres/villainess",
            "Virtual Reality" to "genres/virtual-reality",
            "Web Comic" to "genres/web-comic",
            "Webtoon" to "genres/webtoon",
            "Wuxia" to "genres/wuxia",
            "Zombies" to "genres/zombies",
        ),
    )

class StatusFilter :
    SelectValueFilter(
        "Status",
        arrayOf(
            "All" to "",
            "Ongoing" to "1",
            "Completed" to "2",
        ),
    )

class SortFilter :
    SelectValueFilter(
        "Sort by",
        arrayOf(
            "Latest updates" to "last_update",
            "Most popular (month)" to "views_month",
            "Most popular (week)" to "views_week",
            "Most popular (day)" to "views_day",
            "Most popular" to "views",
            "Newest" to "latest",
            "Highest rated" to "score",
            "Most bookmarked" to "bookmarks",
            "Most commented" to "comments",
            "Most chapters" to "chapters",
        ),
    )
