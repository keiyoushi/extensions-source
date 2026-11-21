package eu.kanade.tachiyomi.extension.all.saycomic

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    default: String = "",
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected)
        }
    }
}

class ChapterRangeFilter :
    SelectFilter(
        "Chapters",
        "chapters",
        arrayOf(
            "All chapters" to "",
            "Under 50 chapters" to "under50",
            "50–100 chapters" to "50-100",
            "Over 100 chapters" to "over100",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        "genre",
        arrayOf(
            "All genres" to "",
            "Academy" to "academy",
            "Acting" to "acting",
            "Action" to "action",
            "Adaptation" to "adaptation",
            "Adventure" to "adventure",
            "Apocalypse" to "apocalypse",
            "Comedy" to "comedy",
            "Cultivation" to "cultivation",
            "Demons" to "demons",
            "Drama" to "drama",
            "Dungeon" to "dungeon",
            "Fantasy" to "fantasy",
            "Historical" to "historical",
            "Horror" to "horror",
            "Hunter" to "hunter",
            "Isekai" to "isekai",
            "Magic" to "magic",
            "Martial Arts" to "martial-arts",
            "Mecha" to "mecha",
            "Medical" to "medical",
            "Military" to "military",
            "Mystery" to "mystery",
            "Necromancer" to "necromancer",
            "Overpowered" to "overpowered",
            "Psychological" to "psychological",
            "Rebirth" to "rebirth",
            "Regression" to "regression",
            "Reincarnation" to "reincarnation",
            "Returner" to "returner",
            "Revenge" to "revenge",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Supernatural" to "supernatural",
            "Survival" to "survival",
            "System" to "system",
            "Thriller" to "thriller",
            "Time Travel" to "time-travel",
            "Tower" to "tower",
            "Villain" to "villain",
            "Webtoon" to "webtoon",
            "Wuxia" to "wuxia",
            "Xuanhuan" to "xuanhuan",
        ),
    )
