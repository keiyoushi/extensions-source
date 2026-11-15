package eu.kanade.tachiyomi.extension.fr.aniverse

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) :
    Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    protected open val param = ""
    override fun addToUrl(url: HttpUrl.Builder) {
        options[state].second?.let { url.addQueryParameter(param, it) }
    }
}

class SortFilter : SelectFilter("Trier par", SORTS) {
    override val param = "sort"
}

class KindFilter : SelectFilter("Origine", KINDS) {
    override val param = "kind"
}

class Option(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroupFilter(name: String, private val param: String, options: List<Pair<String, String>>) :
    Filter.Group<Option>(name, options.map { Option(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class StatusFilter : CheckBoxGroupFilter("Statut", "status", STATUSES)

class TypeFilter : CheckBoxGroupFilter("Format", "type", TYPES)

class GenreFilter : CheckBoxGroupFilter("Genres (tous requis)", "genres", GENRES)

private val SORTS = listOf(
    "Popularité" to "popularity",
    "Note" to "rating",
    "Titre" to "title",
    "Plus récents" to "newest",
)

private val KINDS = listOf(
    "Toutes" to null,
    "Manga (Japon)" to "manga",
    "Manhwa (Corée)" to "manhwa",
    "Manhua (Chine)" to "manhua",
    "Autres comics" to "comic",
)

private val STATUSES = listOf(
    "En cours" to "Ongoing",
    "Terminé" to "Finished",
    "À venir" to "Upcoming",
    "En pause" to "On hiatus",
    "Annulé" to "Cancelled",
)

private val TYPES = listOf(
    "Manga" to "Manga",
    "One-shot" to "One-shot",
)

// French label shown on the site to the value expected by the API.
private val GENRES = listOf(
    "Action" to "Action",
    "Aventure" to "Adventure",
    "Comédie" to "Comedy",
    "Drame" to "Drama",
    "Ecchi" to "Ecchi",
    "Fantasy" to "Fantasy",
    "Horreur" to "Horror",
    "Mahou shoujo" to "Mahou Shoujo",
    "Mecha" to "Mecha",
    "Musique" to "Music",
    "Mystère" to "Mystery",
    "Psychologique" to "Psychological",
    "Romance" to "Romance",
    "Science-fiction" to "Sci-Fi",
    "Tranche de vie" to "Slice of Life",
    "Sport" to "Sports",
    "Surnaturel" to "Supernatural",
    "Thriller" to "Thriller",
)
