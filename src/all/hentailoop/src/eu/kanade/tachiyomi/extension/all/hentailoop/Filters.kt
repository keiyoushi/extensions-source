package eu.kanade.tachiyomi.extension.all.hentailoop

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter :
    Filter.Select<String>(
        name = "Sort",
        values = sortValues.map { it.first }.toTypedArray(),
    ) {
    val sort get() = sortValues[state].second
}

private val sortValues = listOf(
    "Views" to "views",
    "Date" to "date",
    "Likes" to "likes",
    "Dislikes" to "dislikes",
)

class CheckBoxFilter(
    name: String,
    val value: SourceFilter,
) : Filter.CheckBox(name)

class GenreFilter(
    values: List<SourceFilter>,
) : Filter.Group<CheckBoxFilter>(
    name = "Genre",
    state = values.map { CheckBoxFilter(it.name, it) },
) {
    val checked get() = state.filter { it.state }.map { it.value }
}

class ReleaseFilter(val releases: List<SourceFilter>) :
    Filter.Select<String>(
        name = "Release",
        values = buildList {
            add("")
            releases.mapTo(this) { it.name }
        }.toTypedArray(),
    ) {
    val release get() = releases.find { it.name == values[state] }
}

class TriState(name: String, val value: SourceFilter) : Filter.TriState(name)

open class TriStateFilter(
    name: String,
    values: List<SourceFilter>,
) : Filter.Group<TriState>(
    name = name,
    state = values.map { TriState(it.name, it) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }
}

class TagFilter(values: List<SourceFilter>) : TriStateFilter("Tags", values)
class ParodyFilter(values: List<SourceFilter>) : TriStateFilter("Parodies", values)
class ArtistFilter(values: List<SourceFilter>) : TriStateFilter("Artists", values)
class CharacterFilter(values: List<SourceFilter>) : TriStateFilter("Characters", values)
class CircleFilter(values: List<SourceFilter>) : TriStateFilter("Circles", values)
class ConventionFilter(values: List<SourceFilter>) : TriStateFilter("Conventions", values)
class LanguageFilter(values: List<SourceFilter>) : TriStateFilter("Languages", values)

open class PageCountFilter(name: String, state: String) : Filter.Text(name, state) {
    val count get() = state.toIntOrNull().let {
        if (it == null || it < 0) {
            throw Exception("Page Count must be a positive integer")
        }
        it
    }
}

class MinPageCount : PageCountFilter("Minimum Pages", "0")
class MaxPageCount : PageCountFilter("Maximum Pages", "2000")

class UncensoredFilter : Filter.CheckBox("Only Uncensored", false)

fun FilterList.findActiveFilter(): Pair<String, String?>? {
    var activeDirectory: Pair<String, String?>? = null

    for (f in this) {
        when (f) {
            is SortFilter -> Unit
            is TriStateFilter -> when {
                f.included.size == 1 && f.excluded.isEmpty() -> {
                    if (activeDirectory != null) return null
                    val directory = when (f) {
                        is TagFilter -> "tag"
                        is ParodyFilter -> "parodies"
                        is ArtistFilter -> "artists"
                        is CharacterFilter -> "characters"
                        is CircleFilter -> "circles"
                        is ConventionFilter -> "conventions"
                        is LanguageFilter -> "languages"
                        else -> return null
                    }
                    activeDirectory = directory to f.included.first().slug
                }
                f.included.isEmpty() && f.excluded.isEmpty() -> Unit
                else -> return null
            }
            is GenreFilter -> when {
                f.checked.size == 1 -> {
                    if (activeDirectory != null) return null
                    activeDirectory = "genres" to f.checked.first().slug
                }
                f.checked.isEmpty() -> Unit
                else -> return null
            }
            is ReleaseFilter -> when {
                f.state != 0 -> {
                    if (activeDirectory != null) return null
                    activeDirectory = "releases" to (f.release?.slug ?: return null)
                }
                else -> Unit
            }
            is MinPageCount -> if (f.count != 0) return null
            is MaxPageCount -> if (f.count != 2000) return null
            is UncensoredFilter -> if (f.state) return null
            else -> throw IllegalStateException("unknown filter")
        }
    }

    return activeDirectory ?: ("manga" to null)
}
