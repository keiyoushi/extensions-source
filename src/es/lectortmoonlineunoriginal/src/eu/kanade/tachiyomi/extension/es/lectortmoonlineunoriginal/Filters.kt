package eu.kanade.tachiyomi.extension.es.lectortmoonlineunoriginal

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter : SelectFilter("Orden", SORTS, SORTS.indexOfFirst { it.second == "views" })

class TypeFilter : SelectFilter("Tipo", TYPES)

class StatusFilter : SelectFilter("Estado", STATUSES)

class GenreFilter(genres: List<GenreDto>) : SelectFilter("Género", listOf("Ver todo" to "") + genres.map { it.name to it.slug })

private val SORTS = listOf(
    "Más Recientes" to "date",
    "Más Populares" to "views",
    "Mejor Valorados" to "score",
    "Alfabético" to "title",
)

private val TYPES = listOf(
    "Ver todo" to "",
    "Manga" to "Manga",
    "Manhwa" to "Manhwa",
    "Manhua" to "Manhua",
)

private val STATUSES = listOf(
    "Ver todo" to "",
    "En Emisión" to "En curso",
    "Completado" to "Finalizado",
    "Hiatus" to "Pausado",
)
