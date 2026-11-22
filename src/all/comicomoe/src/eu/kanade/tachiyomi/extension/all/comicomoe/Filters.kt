package eu.kanade.tachiyomi.extension.all.comicomoe

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
        "Sort",
        "sort",
        listOf(
            "Relevance" to "relevance",
            "Popular" to "popular",
            "Latest" to "latest",
            "Title" to "title",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
            "Unknown" to "unknown",
        ),
    )

class FormatFilter :
    SelectFilter(
        "Format",
        "format",
        listOf(
            "Any" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
        ),
    )

class CheckBoxOption(name: String, val value: String, state: Boolean = false) : Filter.CheckBox(name, state)

open class CheckBoxGroup(
    name: String,
    private val param: String,
    options: List<CheckBoxOption>,
) : Filter.Group<CheckBoxOption>(name, options),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class DemographicFilter :
    CheckBoxGroup(
        "Demographic",
        "demographic",
        listOf("Shounen", "Shoujo", "Seinen", "Josei").map { CheckBoxOption(it, it.lowercase()) },
    )

class ContentRatingFilter(selected: Set<String>) :
    CheckBoxGroup(
        "Content rating",
        "contentRating",
        CONTENT_RATINGS.map { (name, value) -> CheckBoxOption(name, value, value in selected) },
    )

class TagFilter(group: String, tags: List<TagDto>) :
    CheckBoxGroup(
        "${group.replaceFirstChar(Char::uppercase)} tags",
        "tags",
        tags.map { CheckBoxOption(it.name, it.id) },
    )

class ComickTagsFilter :
    Filter.Text("Comick tags (comma-separated)"),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { url.addQueryParameter("comickTags", it) }
    }
}

val CONTENT_RATINGS = listOf(
    "Safe" to "safe",
    "Suggestive" to "suggestive",
    "Erotica" to "erotica",
    "Pornographic" to "pornographic",
)
