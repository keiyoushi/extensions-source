package eu.kanade.tachiyomi.extension.es.submanhwa

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
    default: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), default) {
    val selected get() = options[state].second
}

class SortFilter :
    UriPartFilter(
        "Ordenar",
        "sort",
        listOf(
            "Actualizados" to "",
            "Nuevos" to "new",
            "Populares" to "popular",
            "A - Z" to "az",
        ),
        2,
    )

class TypeFilter :
    UriPartFilter(
        "Tipo",
        "type",
        listOf(
            "Todos" to "",
            "+18" to "1",
            "BL" to "6",
            "Shoujo" to "11",
            "Manga" to "mangas",
            "Comic" to "8",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Estado",
        "status",
        listOf(
            "Todos" to "",
            "En Curso" to "1",
            "Completa" to "2",
        ),
    )

class GenreFilter(genres: List<Pair<String, String>>) :
    UriPartFilter(
        "Género",
        "genre",
        listOf("Todos" to "") + genres,
    )
