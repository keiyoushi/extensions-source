package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SectionFilter : Filter.Select<String>("Section", SECTIONS.map { it.first }.toTypedArray()) {
    val value get() = SECTIONS[state].second
}

class OptionCheckBox(name: String, val value: String) : Filter.CheckBox(name)

open class OptionGroupFilter(name: String, options: List<Pair<String, String>>) : Filter.Group<OptionCheckBox>(name, options.map { OptionCheckBox(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.value }
}

class TypeFilter : OptionGroupFilter("Work type", TYPES)

class SortFilter(state: Int = 0) : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray(), state) {
    val value get() = SORTS[state].second

    companion object {
        val LATEST get() = FilterList(SortFilter(1))
    }
}

class NoAiFilter : Filter.CheckBox("Exclude AI-generated works")

class GenreFilter(genres: List<GenreOption>) : OptionGroupFilter("Genres (works with all of them)", genres.map { it.name to it.id })

// The maniax search also covers the adult books (BJ works)
private val SECTIONS = listOf(
    "Doujin and Adult Comics (R18)" to "maniax",
    "All Ages" to "home",
    "Otome (R18)" to "girls",
    "BL (R18)" to "bl",
    "My library (log in via WebView)" to DLsite.LIBRARY_SECTION,
)

// None checked means both, like the lists
private val TYPES = listOf(
    "Manga" to "comic",
    "CG + Illustrations" to "illust",
)

private val SORTS = listOf(
    "Trending" to "trend",
    "Newest" to "release_d",
    "Best Selling" to "dl_d",
    "Highest Rated" to "rate_d",
    "Most Reviewed" to "review_d",
    "Price: Low to High" to "price",
    "Price: High to Low" to "price_d",
)
