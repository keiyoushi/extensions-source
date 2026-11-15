package eu.kanade.tachiyomi.extension.all.hentaipaw

import eu.kanade.tachiyomi.source.model.Filter

enum class SortOption(val title: String, val type: String?) {
    POPULAR("Popular", "popular"),
    RECENT("Recent", null),
}

class SortFilter : Filter.Select<String>("Sort", SortOption.entries.map { it.title }.toTypedArray()) {
    val selected get() = SortOption.entries[state]
}
