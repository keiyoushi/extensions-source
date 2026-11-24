package eu.kanade.tachiyomi.extension.en.ocecomic

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sort",
        listOf(
            "Latest" to "latest",
            "Popular" to "popular",
            "Older" to "older",
            "Newer" to "newer",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        listOf(
            "All" to "all",
            "Action" to "action",
            "Adventure" to "adventure",
            "Anthology" to "anthology",
            "Apocalyptic" to "apocalyptic",
            "Children" to "children",
            "Comedy" to "comedy",
            "Crime" to "crime",
            "Drama" to "drama",
            "Dystopia" to "dystopia",
            "Fantasy" to "fantasy",
            "Game" to "game",
            "Heroine" to "heroine",
            "Historical" to "historical",
            "Horror" to "horror",
            "Kingdom" to "kingdom",
            "Literature" to "literature",
            "Mature" to "mature",
            "Military" to "military",
            "Monster" to "monster",
            "Mystery" to "mystery",
            "Mythology" to "mythology",
            "Occult" to "occult",
            "Political" to "political",
            "Robot" to "robot",
            "Romance" to "romance",
            "Sci-Fi" to "sci-fi",
            "Slice of Life" to "slice-of-life",
            "Space" to "space",
            "Spy" to "spy",
            "Superhero" to "superhero",
            "Supernatural" to "supernatural",
            "Suspense" to "suspense",
            "Thriller" to "thriller",
            "Vampire" to "vampire",
            "War" to "war",
            "Western" to "western",
            "Zombie" to "zombie",
        ),
    )
