package eu.kanade.tachiyomi.extension.en.toonkotsu

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter : Filter.Sort("Sort", SORTS.map { it.first }.toTypedArray(), Selection(0, false)) {
    val value get() = SORTS[state?.index ?: 0].second
    val order get() = if (state?.ascending == true) "asc" else "desc"
}

private val SORTS = listOf(
    "Relevance" to "relevance",
    "Popular" to "popular",
    "Latest upload" to "latest",
    "Recently added" to "new",
    "Rating" to "score",
    "Title" to "title",
    "Year" to "year",
    "Chapters" to "chapters",
)

class TypeFilter(types: List<String>) : SelectFilter("Type", "type", listOf("Any" to "") + types.map { it to it })

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(
    name: String,
    val param: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }) {
    val values get() = state.filter { it.state }.map { it.value }
}

class StatusFilter :
    CheckBoxGroup(
        "Status",
        "status",
        listOf("Ongoing" to "Ongoing", "Complete" to "Complete", "Hiatus" to "Hiatus", "Canceled" to "Canceled"),
    )

class DemographicFilter : CheckBoxGroup("Demographic", "demographic", DEMOGRAPHICS.map { it to it } + ("None" to "none"))

class CountryFilter :
    CheckBoxGroup(
        "Country",
        "originalLanguage",
        listOf("Japan" to "ja", "Korea" to "ko", "China" to "zh", "Western" to "en"),
    )

class TagTriState(name: String) : Filter.TriState(name)

open class TagGroup(name: String, tags: List<String>) : Filter.Group<TagTriState>(name, tags.map(::TagTriState))

class GenreFilter(genres: List<String>) : TagGroup("Genres", genres)

class ThemeFilter(themes: List<String>) : TagGroup("Themes", themes)

class GenreModeFilter : SelectFilter("Included genres and themes", "genreMode", listOf("Match all" to "all", "Match any" to "any"))

class ExcludeModeFilter : SelectFilter("Excluded genres and themes", "excludeMode", listOf("Match any" to "any", "Match all" to "all"))

class NumberFilter(name: String, val param: String) : Filter.Text(name)

val DEMOGRAPHICS = listOf("Shounen", "Shoujo", "Seinen", "Josei")

// Tags the site's own tag picker leaves out
val HIDDEN_TAGS = DEMOGRAPHICS + listOf("Lolicon", "Shotacon", "Other")

val ADULT_TAGS = listOf("Hentai", "Adult", "Smut")
