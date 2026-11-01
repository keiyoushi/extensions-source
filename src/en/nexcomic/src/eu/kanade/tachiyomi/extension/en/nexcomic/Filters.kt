package eu.kanade.tachiyomi.extension.en.nexcomic

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort",
        listOf(
            "Most Viewed" to "viewed",
            "Newest" to "newest",
        ),
    )

class CheckBoxFilter(name: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, values: List<String>) : Filter.Group<CheckBoxFilter>(name, values.map { CheckBoxFilter(it) }) {
    val checked get() = state.filter { it.state }.map { it.name }
}

class TypeFilter : CheckBoxGroup("Types", listOf("Manga", "Manhwa", "Manhua", "Webtoon", "Novel", "Doujinshi", "Other"))

class StatusFilter : CheckBoxGroup("Status", listOf("Ongoing", "Completed"))

class TriStateFilter(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) : Filter.Group<TriStateFilter>("Genres", genres.map { TriStateFilter(it) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.name }
    val excluded get() = state.filter { it.isExcluded() }.map { it.name }
}
