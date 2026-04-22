package eu.kanade.tachiyomi.extension.ru.ninegrid

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Сортировка",
        arrayOf("По популярности", "По новизне", "По названию", "По году"),
    ) {
    val selected: String
        get() = when (state) {
            0 -> "popular"
            1 -> "latest"
            2 -> "name"
            3 -> "year"
            else -> "popular"
        }
}

class PublisherFilter : Filter.Text("Издатель")

class YearFilter : Filter.Text("Год начала")

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)

fun getGenreList(): List<Genre> = listOf(
    Genre("Adult"),
    Genre("Crime"),
    Genre("Espionage"),
    Genre("Fantasy"),
    Genre("Historical"),
    Genre("Horror"),
    Genre("Humor"),
    Genre("Manga"),
    Genre("Martial Arts"),
    Genre("Math & Science"),
    Genre("Military"),
    Genre("Mystery"),
    Genre("Mythology"),
    Genre("Political"),
    Genre("Post-Apocalyptic"),
    Genre("Psychological"),
    Genre("Pulp"),
    Genre("Romance"),
    Genre("School Life"),
    Genre("Sci-Fi"),
    Genre("Slice of Life"),
    Genre("Spy"),
    Genre("Superhero"),
    Genre("Supernatural"),
    Genre("Thriller"),
    Genre("War"),
    Genre("Western"),
)
