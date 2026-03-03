package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class SearchType :
    SelectFilter(
        name = "Search Type",
        options = listOf(
            "ALL" to null,
            "Originals" to "originals",
            "Canvas" to "canvas",
        ),
    )
