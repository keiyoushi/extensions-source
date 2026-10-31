package eu.kanade.tachiyomi.extension.en.sacachispa

import eu.kanade.tachiyomi.source.model.Filter

class CatalogFilter : Filter.Select<String>("Catalog", arrayOf("Series", "Commissions")) {
    val commissions get() = state == 1
}

class GenreFilter(genres: List<String>) : Filter.Group<GenreTriState>("Genres", genres.map(::GenreTriState)) {
    val included get() = state.filter { it.isIncluded() }.map { it.name }
    val excluded get() = state.filter { it.isExcluded() }.map { it.name }
}

class GenreTriState(name: String) : Filter.TriState(name)
