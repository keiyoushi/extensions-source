package eu.kanade.tachiyomi.multisrc.crunchyscanst

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Trier par", SORTS.map { it.first }.toTypedArray(), 2),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("sort", SORTS[state].second)
    }
}

class StatusFilter :
    Filter.Select<String>("Statut", STATUSES.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        STATUSES[state].second?.let { url.addQueryParameter("status", it) }
    }
}

class Option(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroupFilter(name: String, private val param: String, values: List<String>) :
    Filter.Group<Option>(name, values.map { Option(it, it) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class TypeFilter : CheckBoxGroupFilter("Type", "type", TYPES)

class GenreFilter : CheckBoxGroupFilter("Genres", "genre", GENRES)

private val SORTS = listOf(
    "Tendance" to "tendance",
    "Nouveautés" to "nouveautes",
    "Populaire" to "populaire",
    "Note" to "note",
    "A-Z" to "az",
)

private val STATUSES = listOf(
    "Tous" to null,
    "En cours" to "En Cours",
    "Terminé" to "Terminé",
    "En pause" to "En Pause",
    "Abandonné" to "Abandonné",
)

private val TYPES = listOf(
    "Manga",
    "Manhwa",
    "Manhua",
    "Comics",
    "BD",
    "Global-Manga",
    "Novel",
    "Artbook",
    "Guidebook",
    "Anime-Comics",
    "Fanfiction",
)

private val GENRES = listOf(
    "Fantastique",
    "Action",
    "Aventure",
    "Drame",
    "Romance",
    "Comédie",
    "Science-Fiction",
    "Slice of Life",
    "Fantasy",
    "School-Life",
    "Surnaturel",
    "Historique",
    "Mystère",
    "Thriller",
    "Horreur",
    "Psychologique",
    "Mature",
    "Tragique",
    "Ecchi",
    "Histoires courtes",
    "Polar",
    "Erotique",
    "Sport",
    "Biographique",
    "Shônen-aï",
    "Shôjo-aï",
    "Isekai",
    "Nekketsu",
    "Documentaire",
    "Yonkoma",
)
