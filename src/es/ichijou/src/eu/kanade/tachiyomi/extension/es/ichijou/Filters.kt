package eu.kanade.tachiyomi.extension.es.ichijou

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : Filter.Select<String>("Tipo", arrayOf("Todos", "Manga", "Manhwa", "Manhua", "One Shot"))

class SortFilter : Filter.Select<String>("Orden", arrayOf("Popularidad", "Título (A-Z)", "Título (Z-A)"))

class GenreFilter(genres: List<String>) : Filter.Select<String>("Género", (listOf("Todos") + genres).toTypedArray()) {
    val selectedGenre: String?
        get() = values[state].takeIf { state > 0 }
}
