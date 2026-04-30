package eu.kanade.tachiyomi.extension.en.hentainexus

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class OffsetPageFilter : Filter.Text("Offset results by # pages")

class TagFilter : AdvSearchEntryFilter("Tags")
class ArtistFilter : AdvSearchEntryFilter("Artists")
class AuthorFilter : AdvSearchEntryFilter("Authors")
class CircleFilter : AdvSearchEntryFilter("Circles")
class EventFilter : AdvSearchEntryFilter("Events")
class ParodyFilter : AdvSearchEntryFilter("Parodies", "parody")
class MagazineFilter : AdvSearchEntryFilter("Magazines")
class PublisherFilter : AdvSearchEntryFilter("Publishers")

open class AdvSearchEntryFilter(
    name: String,
    val key: String = name.lowercase().removeSuffix("s"),
) : Filter.Text(name)

private class AdvSearchEntry(val key: String, val text: String, val exclude: Boolean)

private fun splitFilterState(state: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    for (ch in state) {
        when {
            ch == '"' -> {
                inQuotes = !inQuotes
                current.append(ch)
            }
            ch == ',' && !inQuotes -> {
                val token = current.toString().trim()
                if (token.isNotEmpty()) tokens.add(token)
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    val last = current.toString().trim()
    if (last.isNotEmpty()) tokens.add(last)
    return tokens
}

internal fun combineQuery(filters: FilterList): String {
    val advSearch = filters.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
        splitFilterState(filter.state).map { token ->
            val exclude = token.startsWith("-")
            val text = token.removePrefix("-")
            AdvSearchEntry(filter.key, text, exclude)
        }
    }

    return buildString {
        advSearch.forEach { entry ->
            if (entry.exclude) append("-")
            append(entry.key)
            append(":")
            append(entry.text)
            append(" ")
        }
    }
}
