package eu.kanade.tachiyomi.extension.en.greedscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val queryParam: String,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(queryParam, value)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
    private val queryParam: String,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            url.addQueryParameter(queryParam, it.value)
        }
    }
}

class SortFilter(defaultValue: String? = null) :
    SelectFilter(
        name = "Sort By",
        options = listOf(
            "Popular" to "popular",
            "Latest Update" to "latest_update",
            "Rating" to "rating",
            "A-Z" to "a_z",
            "Newest" to "newest",
        ),
        queryParam = "sort_by",
        defaultValue = defaultValue,
    ) {
    companion object {
        val popular = FilterList(SortFilter("popular"))
        val latest = FilterList(SortFilter("latest_update"))
    }
}

class StatusFilter :
    CheckBoxGroup(
        name = "Status",
        options = listOf(
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
        ),
        queryParam = "status",
    )

class TypeFilter :
    CheckBoxGroup(
        name = "Type",
        options = listOf(
            "Free" to "free",
            "Coin" to "coin",
            "VIP" to "vip",
        ),
        queryParam = "type",
    )

class MinChaptersFilter :
    Filter.Text("Minimum Chapters"),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        if (state.isNotEmpty()) url.addQueryParameter("min_chapters", state)
    }
}

class GenreFilter :
    CheckBoxGroup(
        name = "Genres",
        options = listOf(
            "Action" to "action",
            "Fantasy" to "fantasy",
            "Adventure" to "adventure",
            "Drama" to "drama",
            "Comedy" to "comedy",
            "Romance" to "romance",
            "Shounen" to "shounen",
            "Isekai" to "isekai",
            "Murim" to "murim",
            "Reincarnation" to "reincarnation",
            "Manhwa" to "manhwa",
            "Mystery" to "mystery",
            "Tragedy" to "tragedy",
            "Webtoons" to "webtoons",
            "Revenge" to "revenge",
            "Slice of Life" to "slice-of-life",
            "Martial Arts" to "martial-arts",
            "School Life" to "school-life",
            "Regression" to "regression",
            "Overpowered" to "overpowered",
            "Historical" to "historical",
            "Supernatural" to "supernatural",
            "Game" to "game",
            "Genius MC" to "genius-mc",
            "System" to "system",
            "Magic" to "magic",
            "Shoujo" to "shoujo",
            "Sci-Fi" to "sci-fi",
            "Wuxia" to "wuxia",
            "Horror" to "horror",
            "Seinen" to "seinen",
            "Psychological" to "psychological",
            "Manhua" to "manhua",
            "Sports" to "sports",
            "Thriller" to "thriller",
            "Dungeons" to "dungeons",
            "Crime" to "crime",
            "Demon" to "demon",
            "Superhero" to "superhero",
            "Crazy MC" to "crazy-mc",
            "Sci Fi" to "sci-fi",
            "Harem" to "harem",
            "Necromancer" to "necromancer",
            "Tower" to "tower",
            "Full Color" to "full-color",
            "Violence" to "violence",
            "Ecchi" to "ecchi",
            "Adaptation" to "adaptation",
            "Long Strip" to "long-strip",
            "Villain" to "villain",
            "Mature" to "mature",
            "Comic" to "comic",
            "Monsters" to "monsters",
            "Medical" to "medical",
            "Manga" to "manga",
            "Time Travel" to "time-travel",
            "Cooking" to "cooking",
            "One Shot" to "one-shot",
        ),
        queryParam = "genres[]",
    )
