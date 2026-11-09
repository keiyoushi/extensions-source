package eu.kanade.tachiyomi.extension.all.kusowanka

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class TermFilter(name: String, val type: String) : Filter.Text(name)

fun getFilters() = FilterList(
    Filter.Header("Search text and fields take names separated by commas, with - in front to exclude"),
    TermFilter("Artists", "artists"),
    TermFilter("Characters", "characters"),
    TermFilter("Parodies", "parodies"),
    TermFilter("Metadatas", "metadatas"),
)
