package eu.kanade.tachiyomi.extension.es.nartag

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : Filter.Select<String>("Tipo", arrayOf("Todos", "Manga", "Manhwa", "Manhua", "Other"))

class StatusFilter : Filter.Select<String>("Estado", arrayOf("Todos", "Ongoing", "Completed", "Hiatus", "Cancelled"))

class SortFilter : Filter.Select<SortOption>("Ordenar por", sortOptions)

class SortOption(val name: String, val value: String) {
    override fun toString(): String = name
}

class GenreFilter(genres: Array<String>) : Filter.Select<String>("Géneros", genres)

val sortOptions = arrayOf(
    SortOption("Más reciente", "latest"),
    SortOption("Actualizado", "updated"),
    SortOption("Más visto", "views"),
    SortOption("Mejor valorado", "rating"),
    SortOption("A-Z", "title"),
)

val genresList = arrayOf(
    "Todos",
    "Acción",
    "Adventure",
    "Aventura",
    "ciencia ficción",
    "Comedia",
    "Cultivación",
    "Drama",
    "Fantasia",
    "Fantasía",
    "Harem",
    "Love",
    "Manhua",
    "Reencarnacion",
    "Romance",
    "Sistema",
    "Supernatural",
    "+15",
)
