package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class OrderByFilter(displayName: String, vals: Array<Pair<String, String>>) : UriPartFilter(displayName, vals)
class StatusFilter(displayName: String, vals: Array<Pair<String, String>>) : UriPartFilter(displayName, vals)
class TypeFilter(displayName: String, vals: Array<Pair<String, String>>) : UriPartFilter(displayName, vals)
class Genre(name: String) : Filter.CheckBox(name)
class GenreFilter(displayName: String, genres: List<Genre>) : Filter.Group<Genre>(displayName, genres)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

fun getFilters() = FilterList(
    OrderByFilter("Trier par", getSortList()),
    Filter.Separator(),
    StatusFilter("Statut", getStatusList()),
    Filter.Separator(),
    TypeFilter("Type", getTypeList()),
    Filter.Separator(),
    GenreFilter("Genres", getGenreList()),
)

private fun getSortList() = arrayOf(
    Pair("Plus populaires", "popular"),
    Pair("Plus récents", "latest"),
    Pair("Plus anciens", "oldest"),
    Pair("Titre A-Z", "titleAZ"),
    Pair("Titre Z-A", "titleZA"),
    Pair("Plus de chapitres", "mostChapters"),
)

private fun getStatusList() = arrayOf(
    Pair("Tous", "all"),
    Pair("En cours", "ongoing"),
    Pair("Terminé", "completed"),
)

private fun getTypeList() = arrayOf(
    Pair("Tous", "all"),
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
    Pair("Webtoon", "webtoon"),
)

private fun getGenreList() = listOf(
    Genre("Action"),
    Genre("Aventure"),
    Genre("Comédie"),
    Genre("Drame"),
    Genre("Fantaisie"),
    Genre("Horreur"),
    Genre("Arts Martiaux"),
    Genre("Mystère"),
    Genre("Psychologique"),
    Genre("Romance"),
    Genre("Tranche de vie"),
    Genre("Surnaturel"),
    Genre("Magie"),
    Genre("Historique"),
    Genre("School Life"),
    Genre("Shonen"),
    Genre("Seinen"),
)
