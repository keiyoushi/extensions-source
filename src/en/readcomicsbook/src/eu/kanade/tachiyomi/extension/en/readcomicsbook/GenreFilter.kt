package eu.kanade.tachiyomi.extension.en.readcomicsbook

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter : Filter.Select<String>(
    "Genres",
    genres.map { it.first }.toTypedArray(),
) {
    val selected get() = genres[state].second
}

private val genres = listOf(
    "Marvel" to "marvel",
    "DC Comics" to "dc-comics",
    "Action" to "action",
    "Adventure" to "adventure",
    "Anthology" to "anthology",
    "Anthropomorphic" to "anthropomorphic",
    "Biography" to "biography",
    "Children" to "children",
    "Comedy" to "comedy",
    "Crime" to "crime",
    "Cyborgs" to "cyborgs",
    "Dark Horse" to "dark-horse",
    "Demons" to "demons",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Family" to "family",
    "Fighting" to "fighting",
    "Gore" to "gore",
    "Graphic Novels" to "graphic-novels",
    "Historical" to "historical",
    "Horror" to "horror",
    "Leading Ladies" to "leading-ladies",
    "Literature" to "literature",
    "Magic" to "magic",
    "Manga" to "manga",
)
