package eu.kanade.tachiyomi.extension.en.dorotoons

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        options[state].second.takeIf { it.isNotEmpty() }?.let { url.addQueryParameter(param, it) }
    }
}

class SortFilter :
    SelectFilter(
        "Sort by",
        "sort",
        listOf(
            "Recently updated" to "",
            "Most read" to "popular",
            "Top rated" to "rated",
            "Newly added" to "newest",
            "A to Z" to "title",
            "Most chapters" to "chapters",
            "Most followed" to "followed",
        ),
    )

class GenreModeFilter :
    SelectFilter(
        "Ticked genres",
        "gmode",
        listOf("Must have every one" to "", "Any one is enough" to "any"),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        "kind",
        listOf("Any type" to "", "Manhwa" to "manhwa", "Manhua" to "manhua", "Manga" to "manga", "Webtoon" to "webtoon"),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf("Any status" to "", "Ongoing" to "ongoing", "Completed" to "completed", "Hiatus" to "hiatus", "Dropped" to "dropped"),
    )

class DayFilter :
    SelectFilter(
        "New chapters on",
        "day",
        listOf(
            "Any day" to "",
            "Monday" to "mon",
            "Tuesday" to "tue",
            "Wednesday" to "wed",
            "Thursday" to "thu",
            "Friday" to "fri",
            "Saturday" to "sat",
            "Sunday" to "sun",
        ),
    )

class RatingFilter :
    SelectFilter(
        "Rating",
        "minrate",
        listOf("Any rating" to "", "★ 3 and up" to "3", "★ 3.5 and up" to "3.5", "★ 4 and up" to "4", "★ 4.5 and up" to "4.5"),
    )

class LengthFilter :
    SelectFilter(
        "Length",
        "minch",
        listOf("Any length" to "", "10+ chapters" to "10", "30+ chapters" to "30", "50+ chapters" to "50", "100+ chapters" to "100"),
    )

class YearFilter(name: String, private val param: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.trim().toIntOrNull()?.let { url.addQueryParameter(param, it.toString()) }
    }
}

class GenreOption(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter(genres: List<Pair<String, String>>) :
    Filter.Group<GenreOption>("Genres", genres.map { GenreOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.forEach {
            when {
                it.isIncluded() -> url.addQueryParameter("genre", it.slug)
                it.isExcluded() -> url.addQueryParameter("xgenre", it.slug)
            }
        }
    }
}
