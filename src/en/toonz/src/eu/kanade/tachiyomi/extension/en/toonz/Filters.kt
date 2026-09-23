package eu.kanade.tachiyomi.extension.en.toonz

import eu.kanade.tachiyomi.source.model.Filter

class CatalogFilter(
    private val options: List<Pair<String, String>> = listOf(
        "All" to "comics",
        "Manhwa" to "manhwa/browse",
        "Manga" to "manga/browse",
        "Western" to "western/browse",
        "Adult" to "adult/browse",
    ),
) : Filter.Select<String>("Catalog", options.map { it.first }.toTypedArray()) {
    fun getValue(): String = options[state].second
}

class SortFilter(
    private val options: List<Pair<String, String>> = listOf(
        "Popular" to "popular",
        "Latest updates" to "latest",
    ),
) : Filter.Select<String>("Sort", options.map { it.first }.toTypedArray()) {
    fun getValue(): String = options[state].second
}

class GenreFilter(
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>("Genre", options.map { it.first }.toTypedArray()) {
    fun getValue(): String = options[state].second
}
