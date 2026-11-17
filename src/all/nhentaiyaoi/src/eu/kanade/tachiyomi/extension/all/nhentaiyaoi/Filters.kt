package eu.kanade.tachiyomi.extension.all.nhentaiyaoi

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterData(val genres: List<GenreRoute>, val languages: List<GenreRoute>)

class SortFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

open class RouteFilter(name: String, private val routes: List<GenreRoute>) : Filter.Select<String>(name, arrayOf("All") + routes.map(GenreRoute::name)) {
    val value get() = routes.getOrNull(state - 1)
}

class LanguageFilter(languages: List<GenreRoute>) : RouteFilter("Language", languages)

class GenreFilter(name: String, genres: List<GenreRoute>) : RouteFilter(name, genres)
