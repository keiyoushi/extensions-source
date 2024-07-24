package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.source.model.Filter

class Genre(title: String, val id: Int) : Filter.CheckBox(title)
class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

class StatusFilter(title: String, statuses: List<Pair<String, String>>) : UriPartFilter(title, statuses)

class TypeFilter(title: String, types: List<Pair<String, String>>) : UriPartFilter(title, types)

class OrderFilter(title: String, orders: List<Pair<String, String>>) : UriPartFilter(title, orders)

open class UriPartFilter(displayName: String, val vals: List<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
