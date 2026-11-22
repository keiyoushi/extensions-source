package eu.kanade.tachiyomi.extension.en.fanon

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf("Popular", "Latest", "A to Z"),
    ) {
    val value get() = values[state]
}

class FandomFilter(private val fandoms: List<FandomDto>) :
    Filter.Select<String>(
        "Fandom",
        arrayOf("All") + fandoms.map { it.name },
    ) {
    val value get() = fandoms.getOrNull(state - 1)?.fandomId
}
