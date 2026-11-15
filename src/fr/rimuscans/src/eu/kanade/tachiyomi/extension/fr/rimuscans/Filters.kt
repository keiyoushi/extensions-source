package eu.kanade.tachiyomi.extension.fr.rimuscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Trier par",
        arrayOf(
            "Dernière mise à jour" to "updated",
            "Note" to "rating",
            "Nombre de chapitres" to "chapters",
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            "Tous" to "",
            "Manhwa" to "webtoon",
            "Manga" to "manga",
        ),
    )

class StatusCheckBox(name: String, val value: String) : Filter.CheckBox(name)
class StatusFilter :
    Filter.Group<Filter.CheckBox>(
        "Statut",
        listOf(
            StatusCheckBox("En cours", "ongoing"),
            StatusCheckBox("Terminé", "completed"),
            StatusCheckBox("En pause", "hiatus"),
        ),
    )

class PremiumOnlyFilter : Filter.CheckBox("Premium uniquement", false)

class MinChaptersFilter :
    UriPartFilter(
        "Minimum de chapitres",
        arrayOf(
            "Tous" to "",
            "10+" to "10",
            "50+" to "50",
            "100+" to "100",
            "200+" to "200",
            "300+" to "300",
            "500+" to "500",
        ),
    )

class GenreCheckBox(name: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<String>) : Filter.Group<Filter.CheckBox>("Genres", genres.map { GenreCheckBox(it) })

fun getRimuFilterList(genres: List<String>?): FilterList {
    val filters = mutableListOf<Filter<*>>(
        Filter.Header("Les filtres sont ignorés par la recherche texte"),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        MinChaptersFilter(),
        PremiumOnlyFilter(),
    )
    if (genres != null) {
        filters += GenreFilter(genres)
    }
    return FilterList(filters)
}
