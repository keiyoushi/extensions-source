package eu.kanade.tachiyomi.extension.fr.aniverse

import eu.kanade.tachiyomi.source.model.Filter

// French label shown on the site to the value expected by the `genre` API parameter.
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

class GenreFilter : Filter.Select<String>("Genre", arrayOf("Tous") + GENRES.map { it.first }) {
    val value get() = if (state == 0) null else GENRES[state - 1].second
}
