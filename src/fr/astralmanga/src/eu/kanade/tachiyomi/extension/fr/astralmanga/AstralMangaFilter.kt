package eu.kanade.tachiyomi.extension.fr.astralmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter(displayName: String, vals: Array<Pair<String, String>>, state: Int = 0) : UriPartFilter(displayName, vals, state)
class StatusFilter(displayName: String, vals: Array<Pair<String, String>>) : UriPartFilter(displayName, vals)
class TypeFilter(displayName: String, vals: Array<Pair<String, String>>) : UriPartFilter(displayName, vals)

class Genre(name: String) : Filter.CheckBox(name)
class GenreFilter(displayName: String, genres: List<Genre>) : Filter.Group<Genre>(displayName, genres)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
}

fun getFilters(genres: List<String>) = FilterList(
    listOf(
        SortFilter("Trier par", getSortList()),
        Filter.Separator(),
        StatusFilter("Statut", getStatusList()),
        Filter.Separator(),
        TypeFilter("Type", getTypeList()),
        Filter.Separator(),
    ) + buildList {
        if (genres.isNotEmpty()) add(GenreFilter("Genres", genres.map(::Genre)))
    },
)

val LATEST = SortFilter("", getSortList(), 2)
val POPULAR = SortFilter("", getSortList(), 4)

private fun getSortList() = arrayOf(
    Pair("Popularité", "popularity"),
    Pair("Titre", "title"),
    Pair("Note", "note"),
    Pair("Date de création", "createdAt"),
    Pair("Date de publication", "publishDate"),
)

private fun getStatusList() = arrayOf(
    Pair("Tout", ""),
    Pair("En cours", "ON_GOING"),
    Pair("Terminé", "COMPLETED"),
    Pair("En pause", "HIATUS"),
    Pair("Annulé", "CANCELLED"),
)

private fun getTypeList() = arrayOf(
    Pair("Tout", ""),
    Pair("Manga", "MANGA"),
    Pair("Manhwa", "MANHWA"),
    Pair("Manhua", "MANHUA"),
    Pair("Novel", "NOVEL"),
)
