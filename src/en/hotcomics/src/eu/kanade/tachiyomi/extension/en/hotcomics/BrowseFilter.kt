package eu.kanade.tachiyomi.extension.en.hotcomics

import eu.kanade.tachiyomi.source.model.Filter

class BrowseFilter(private val options: List<Pair<String, String>>) : Filter.Select<String>("Browse", options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

val browseList = listOf(
    "Ranking" to "en/ranking",
    "Home" to "en",
    "Weekly" to "en/weekly",
    "New" to "en/new",
)
