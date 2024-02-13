package eu.kanade.tachiyomi.multisrc.zeistmanga

import eu.kanade.tachiyomi.source.model.Filter

class Genre(title: String, val value: String) : Filter.CheckBox(title)

class GenreList(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)
class TypeList(title: String, types: List<Type>) : EnhancedSelect<Type>(title, types.toTypedArray())
class LanguageList(title: String, languages: List<Language>) : EnhancedSelect<Language>(title, languages.toTypedArray())
class StatusList(title: String, statuses: List<Status>) : EnhancedSelect<Status>(title, statuses.toTypedArray())

open class EnhancedSelect<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
    val selected: T
        get() = values[state]
}

data class Status(val name: String, val value: String) {
    override fun toString(): String = name
}

data class Type(val name: String, val value: String) {
    override fun toString(): String = name
}
data class Language(val name: String, val value: String) {
    override fun toString(): String = name
}
