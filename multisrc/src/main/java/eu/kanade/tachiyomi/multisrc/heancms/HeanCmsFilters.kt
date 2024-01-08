package eu.kanade.tachiyomi.multisrc.heancms

import eu.kanade.tachiyomi.source.model.Filter

class Genre(title: String, val id: Int) : Filter.CheckBox(title)

class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

open class EnhancedSelect<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
    val selected: T
        get() = values[state]
}

data class Status(val name: String, val value: String) {
    override fun toString(): String = name
}

class StatusFilter(title: String, statuses: List<Status>) :
    EnhancedSelect<Status>(title, statuses.toTypedArray())

data class SortProperty(val name: String, val value: String) {
    override fun toString(): String = name
}

class SortByFilter(title: String, private val sortProperties: List<SortProperty>) : Filter.Sort(
    title,
    sortProperties.map { it.name }.toTypedArray(),
    Selection(1, ascending = false),
) {
    val selected: String
        get() = sortProperties[state!!.index].value
}
