package eu.kanade.tachiyomi.extension.fr.blossomscans

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Trier par",
        arrayOf(
            "Popularité" to "popularity",
            "Récents" to "recents",
            "Nouvelles séries" to "new",
            "A - Z" to "alphabetical",
            "🌸 Blossom" to "blossom",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Statut",
        arrayOf(
            "Tous" to "",
            "En cours" to "ongoing",
            "Terminé" to "completed",
        ),
    )

class GenreCheckBox(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) :
    Filter.Group<GenreCheckBox>(
        "Genres",
        genres.map(::GenreCheckBox),
    ) {
    fun toUriPart(): String = state.filter { it.state }.joinToString(",") { it.name }
}
