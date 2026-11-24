package eu.kanade.tachiyomi.extension.es.mhscans

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected get() = options[state].second
}

class SortFilter(default: String = "views") : SelectFilter("Ordenar por", "sort", sortOptions, sortOptions.indexOfFirst { it.second == default })

class TypeFilter :
    SelectFilter(
        "Tipo",
        "type",
        listOf(
            "Todos" to "",
            "Manga" to "Manga",
            "Manhwa" to "Manhwa",
            "Manhua" to "Manhua",
            "Doujinshi" to "Doujinshi",
            "Otro" to "Other",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Estado",
        "status",
        listOf(
            "Todos" to "",
            "En curso" to "Ongoing",
            "Completado" to "Completed",
            "En pausa" to "Hiatus",
            "Cancelado" to "Cancelled",
        ),
    )

class GenreFilter(genres: List<String>) : SelectFilter("Género", "genre", listOf("Todos" to "") + genres.map { it to it })

private val sortOptions = listOf(
    "Más reciente" to "latest",
    "Actualizado" to "updated",
    "Más visto" to "views",
    "Mejor valorado" to "rating",
    "A-Z" to "title",
)
