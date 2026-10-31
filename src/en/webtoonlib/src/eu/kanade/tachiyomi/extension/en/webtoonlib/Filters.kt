package eu.kanade.tachiyomi.extension.en.webtoonlib

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        options[state].second.takeIf { it.isNotEmpty() }?.let { url.setQueryParameter(param, it) }
    }
}

class SortFilter :
    SelectFilter(
        "Sort by",
        "sort",
        listOf(
            "Popularity" to "popularityAll",
            "Popular this week" to "popularityWeekly",
            "Popular this month" to "popularityMonthly",
            "Recently updated" to "latestChapter",
            "Rating" to "rating",
            "Name (A-Z)" to "alphabetical",
        ),
    )

class ContentTypeFilter :
    SelectFilter(
        "Language",
        "contentType",
        listOf("All" to "", "HardSub" to "HARDSUB", "SoftSub" to "SOFTSUB"),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        "type",
        listOf("All" to "", "Manhwa" to "MANHWA", "Manga" to "MANGA", "Manhua" to "MANHUA"),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf("All" to "", "Ongoing" to "ONGOING", "Completed" to "COMPLETED", "Hiatus" to "HIATUS"),
    )

class GenreFilter(genres: List<String>) : SelectFilter("Category", "genre", listOf("All" to "") + genres.map { it to it })

class ExcludeAdultFilter(state: Boolean) :
    Filter.CheckBox("Hide adult series", state),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        if (state) url.setQueryParameter("excludeAdult", "true")
    }
}
