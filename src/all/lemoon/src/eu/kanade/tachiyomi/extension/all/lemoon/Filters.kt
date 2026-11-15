package eu.kanade.tachiyomi.extension.all.lemoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter : Filter.Group<Genre>("Genres", GENRES.map { Genre(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.id }
}

fun getFilters() = FilterList(GenreFilter())

internal fun genreName(id: String) = GENRES.firstOrNull { it.second == id }?.first ?: id

private val GENRES = listOf(
    "Action" to "action",
    "Adventure" to "adventure",
    "Boys Love" to "boys_love",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Girls Love" to "girls_love",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "LGBTQ+" to "lgbtq_plus",
    "Mystery" to "mystery",
    "One Shot" to "one_shot",
    "Romance" to "romance",
    "Sci-Fi" to "sci_fi",
    "Slice Of Life" to "slice_of_life",
    "Sport" to "sport",
    "Super Hero" to "super_hero",
    "Supernatural" to "supernatural",
)
