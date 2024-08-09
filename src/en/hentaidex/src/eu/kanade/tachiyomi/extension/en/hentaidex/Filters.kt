package eu.kanade.tachiyomi.extension.en.hentaidex

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(name: String, genreList: List<Pair<String, String>>) :
    Filter.Group<TriFilter>(name, genreList.map { TriFilter(it.first, it.second) })

internal class StatusFilter(name: String, statusList: List<Pair<String, String>>) :
    SelectFilter(name, statusList)

internal class TypeFilter(name: String, typeList: List<Pair<String, String>>) :
    SelectFilter(name, typeList)

internal class SortFilter(name: String, sortList: List<Pair<String, String>>) :
    SelectFilter(name, sortList)

internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}
