package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Locale

open class UriPartFilter(name: String, private val options: Array<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = options[state].second
}

class SortFilter(state: Selection = Selection(0, false)) : Filter.Sort("Sort", SORT_OPTIONS.map { it.first }.toTypedArray(), state) {
    fun toUriPart(): String = SORT_OPTIONS[state?.index ?: 0].second

    companion object {
        val POPULAR = FilterList(SortFilter(Selection(2, false)))
    }
}

class StatusFilter : UriPartFilter("Status", STATUS_OPTIONS)

class TypeFilter : UriPartFilter("Type", TYPE_OPTIONS)

class FormatFilter : UriPartFilter("Format", FORMAT_OPTIONS)

class ContentFilter : UriPartFilter("Content", CONTENT_OPTIONS)

class LanguageFilter : UriPartFilter("Language", LANG_OPTIONS)

class YearFilter : Filter.Text("Year")

class GenreFilter(tags: List<String>) : Filter.Group<TagOption>("Genres", tags.map { TagOption(it) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class TagOption(val value: String) : Filter.TriState(value.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } })

private val SORT_OPTIONS = arrayOf(
    "Default" to "relevance",
    "Trending" to "trending",
    "Popular" to "popular",
    "Latest chapter" to "latest_chapter",
    "Title" to "title",
    "Year" to "year",
    "Rating" to "rating",
)

private val STATUS_OPTIONS = arrayOf(
    "Any" to null,
    "Ongoing" to "ongoing",
    "Completed" to "completed",
    "Hiatus" to "hiatus",
    "Cancelled" to "cancelled",
    "Not yet released" to "not_yet_released",
)

private val TYPE_OPTIONS = arrayOf(
    "Any" to null,
    "Manga" to "JP",
    "Manhwa" to "KR",
    "Manhua" to "CN",
)

private val FORMAT_OPTIONS = arrayOf(
    "Any" to null,
    "One-shot" to "oneshot",
    "Doujinshi" to "doujinshi",
)

private val CONTENT_OPTIONS = arrayOf(
    "Any" to null,
    "Safe" to "safe",
    "Suggestive" to "suggestive",
    "Explicit" to "explicit",
)

private val LANG_OPTIONS = arrayOf(
    "Any" to null,
    "English" to "en",
    "Indonesian" to "id",
    "Japanese" to "ja",
    "Arabic" to "ar",
    "Bulgarian" to "bg",
    "German" to "de",
    "Korean" to "ko",
    "Malay" to "ms",
    "Tagalog" to "tl",
    "Vietnamese" to "vi",
    "Chinese" to "zh",
)
