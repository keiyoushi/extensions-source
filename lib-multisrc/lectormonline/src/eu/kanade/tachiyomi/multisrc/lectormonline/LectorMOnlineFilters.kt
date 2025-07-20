package eu.kanade.tachiyomi.multisrc.lectormonline

import eu.kanade.tachiyomi.source.model.Filter

class SortByFilter(title: String, private val sortProperties: List<SortProperty>, defaultIndex: Int) : Filter.Sort(
    title,
    sortProperties.map { it.name }.toTypedArray(),
    Selection(defaultIndex, ascending = false),
) {
    val selected: String
        get() = sortProperties[state!!.index].value
}

class SortProperty(val name: String, val value: String) {
    override fun toString(): String = name
}

class GenreFilter(genres: List<Pair<String, String>>) : UriPartFilter(
    "Género",
    arrayOf(
        Pair("Todos", ""),
        *genres.toTypedArray(),
    ),
)

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
