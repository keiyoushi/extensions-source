package eu.kanade.tachiyomi.extension.en.webnex

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String = "",
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.coerceAtLeast(0),
),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) url.addQueryParameter(param, value)
    }
}

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    private val param: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class TriStateOption(name: String) : Filter.TriState(name)

open class TriStateGroup(
    name: String,
    private val includeParam: String,
    private val excludeParam: String,
    options: List<String>,
) : Filter.Group<TriStateOption>(name, options.map(::TriStateOption)),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.forEach {
            when (it.state) {
                Filter.TriState.STATE_INCLUDE -> url.addQueryParameter(includeParam, it.name)
                Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter(excludeParam, it.name)
            }
        }
    }
}

class SortFilter(default: String = "") :
    SelectFilter(
        "Sort",
        "sort",
        listOf(
            "Most sources" to "coverage",
            "Recently updated" to "updated",
            "Newest releases" to "year_newest",
            "Oldest releases" to "year_oldest",
            "Title A to Z" to "title",
        ),
        default,
    )

class KindFilter :
    CheckBoxGroup(
        "Type",
        "kind",
        listOf(
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )

class StatusFilter :
    CheckBoxGroup(
        "Status",
        "status",
        listOf(
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "On hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        ),
    )

class DemographicFilter :
    CheckBoxGroup(
        "Demographic",
        "demographic",
        listOf(
            "Shounen" to "shounen",
            "Shoujo" to "shoujo",
            "Seinen" to "seinen",
            "Josei" to "josei",
        ),
    )

class LanguageFilter :
    CheckBoxGroup(
        "Original language",
        "language",
        listOf(
            "Japanese" to "ja",
            "Korean" to "ko",
            "Chinese" to "zh",
            "Chinese (Hong Kong)" to "zh-hk",
        ),
    )

class RatingFilter :
    SelectFilter(
        "Content rating",
        "rating",
        listOf(
            "Default (Suggestive)" to "",
            "Up to Safe" to "safe",
            "Up to Suggestive" to "suggestive",
            "Up to Erotica" to "erotica",
            "Up to Pornographic (18+)" to "pornographic",
        ),
    )

class ChaptersFilter :
    SelectFilter(
        "Chapters",
        "chapters",
        listOf(
            "Any" to "",
            "Readable now" to "1",
            "10 or more" to "10",
            "50 or more" to "50",
            "100 or more" to "100",
            "200 or more" to "200",
        ),
    )

class MatchFilter :
    SelectFilter(
        "Genres and tags must match",
        "match",
        listOf(
            "All" to "",
            "Any" to "any",
        ),
    )

class GenreFilter :
    TriStateGroup(
        "Genres",
        "genre",
        "xgenre",
        listOf(
            "Action", "Adventure", "Boys' Love", "Comedy", "Crime", "Drama", "Ecchi", "Fantasy",
            "Gender Bender", "Girls' Love", "Gore", "Historical", "Horror", "Isekai", "Josei",
            "Magical Girls", "Mature", "Mecha", "Medical", "Mystery", "Philosophical",
            "Psychological", "Romance", "Sci-Fi", "Seinen", "Sexual Violence", "Shoujo",
            "Shoujo Ai", "Shounen", "Shounen Ai", "Slice of Life", "Sports", "Superhero",
            "Thriller", "Tragedy", "Wuxia", "Yaoi", "Yuri",
        ),
    )

class FormatFilter :
    TriStateGroup(
        "Formats",
        "tag",
        "xtag",
        listOf(
            "4-Koma", "Adaptation", "Anthology", "Award Winning", "Doujinshi", "Fan Colored",
            "Full Color", "Long Strip", "Official Colored", "Oneshot", "Self-Published",
            "User Created", "Web Comic",
        ),
    )

class ThemeFilter :
    TriStateGroup(
        "Themes",
        "tag",
        "xtag",
        listOf(
            "Aliens", "Animals", "Cooking", "Crossdressing", "Delinquents", "Demons", "Genderswap",
            "Ghosts", "Gyaru", "Harem", "Incest", "Loli", "Mafia", "Magic", "Martial Arts",
            "Military", "Monster Girls", "Monsters", "Music", "Ninja", "Office Workers", "Police",
            "Post-Apocalyptic", "Reincarnation", "Reverse Harem", "Samurai", "School Life", "Shota",
            "Supernatural", "Survival", "Time Travel", "Traditional Games", "Vampires",
            "Video Games", "Villainess", "Virtual Reality", "Zombies",
        ),
    )
