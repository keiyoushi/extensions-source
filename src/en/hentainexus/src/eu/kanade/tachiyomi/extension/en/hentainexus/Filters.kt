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

internal fun combineQuery(filters: FilterList): String {
    val advSearch = filters.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
        filter.state
            .replace("\"", "")
            .split(",")
            .map(String::trim)
            .mapNotNull {
                val exclude = it.startsWith("-")
                val text = it.removePrefix("-").trim()
                if (text.isBlank()) {
                    null
                } else {
                    Pair(exclude, "\"$text\"")
                }
            }
            .map { (exclude, text) -> AdvSearchEntry(filter.key, text, exclude) }
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
