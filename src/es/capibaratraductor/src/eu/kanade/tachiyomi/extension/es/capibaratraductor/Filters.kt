package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.source.model.Filter

class SortByFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

class TypeFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

class StatusFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

class DemographyFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

class GenreOption(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter(title: String, genres: List<Pair<String, String>>) : Filter.Group<GenreOption>(title, genres.map { GenreOption(it.first, it.second) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.slug }
    val excluded get() = state.filter { it.isExcluded() }.map { it.slug }
}

class ScanlatorFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
