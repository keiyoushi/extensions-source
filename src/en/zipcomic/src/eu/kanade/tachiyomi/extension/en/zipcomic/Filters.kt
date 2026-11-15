package eu.kanade.tachiyomi.extension.en.zipcomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("The genre is ignored when a status is chosen"),
    StatusFilter(),
    GenreFilter(),
)

open class QueryFilter(name: String, val param: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class StatusFilter : QueryFilter("Status", "status", listOf("Any" to null, "Ongoing" to "ongoing", "Completed" to "completed"))

class GenreFilter : QueryFilter("Genre", "genre", listOf("Any" to null) + GENRES)

private val GENRES = listOf(
    "Action" to "action",
    "Adventure" to "adventure",
    "Anthology" to "anthology",
    "Anthropomorphic" to "anthropomorphic",
    "Biography" to "biography",
    "Children" to "children",
    "Comedy" to "comedy",
    "Crime" to "crime",
    "Drama" to "drama",
    "Family" to "family",
    "Fantasy" to "fantasy",
    "Fighting" to "fighting",
    "Graphic Novels" to "graphic-novels",
    "Historical" to "historical",
    "Horror" to "horror",
    "Leading Ladies" to "leading-ladies",
    "LGBTQ" to "lgbtq",
    "Literature" to "literature",
    "Manga" to "manga",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Military" to "military",
    "Mini-Series" to "mini-series",
    "Movies & TV" to "movies-tv",
    "Music" to "music",
    "Mystery" to "mystery",
    "Mythology" to "mythology",
    "Personal" to "personal",
    "Political" to "political",
    "Post-Apocalyptic" to "post-apocalyptic",
    "Psychological" to "psychological",
    "Pulp" to "pulp",
    "Religious" to "religious",
    "Robots" to "robots",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-Fi" to "sci-fi",
    "Slice of Life" to "slice-of-life",
    "Sport" to "sport",
    "Spy" to "spy",
    "Superhero" to "superhero",
    "Supernatural" to "supernatural",
    "Suspense" to "suspense",
    "Teen" to "teen",
    "Thriller" to "thriller",
    "Vampires" to "vampires",
    "Video Games" to "video-games",
    "War" to "war",
    "Western" to "western",
    "Zombies" to "zombies",
)
