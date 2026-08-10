package eu.kanade.tachiyomi.multisrc.origines

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxFilter(name: String, val slug: String) : Filter.CheckBox(name)

sealed class CheckBoxGroupFilter(name: String, options: List<Pair<String, String>>) : Filter.Group<CheckBoxFilter>(name, options.map { CheckBoxFilter(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.slug }
}

sealed class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

sealed class CountFilter(name: String) : Filter.Text(name) {
    val value get() = state.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: "0"
}

class OriginFilter(origins: List<Pair<String, String>>) : CheckBoxGroupFilter("Origine", origins)

class GenreFilter(genres: List<Pair<String, String>>) : CheckBoxGroupFilter("Genres", genres)

class StatusFilter : SelectFilter("Statut", STATUSES)

class RatingFilter : SelectFilter("Note minimum", RATINGS)

class SortFilter : SelectFilter("Trier par", SORTS)

class ChapterMinFilter : CountFilter("Chapitres (min)")

class ChapterMaxFilter : CountFilter("Chapitres (max)")

private val STATUSES = listOf(
    "Tous" to "tous",
    "En cours" to "en-cours",
    "Terminé" to "termine",
)

private val RATINGS = listOf(
    "Toutes" to "0",
    "1 étoile et plus" to "1",
    "2 étoiles et plus" to "2",
    "3 étoiles et plus" to "3",
    "4 étoiles et plus" to "4",
    "5 étoiles" to "5",
)

private val SORTS = listOf(
    "Récents" to "recents",
    "Populaire" to "populaire",
    "Mieux notés" to "notes",
    "A → Z" to "az",
)
