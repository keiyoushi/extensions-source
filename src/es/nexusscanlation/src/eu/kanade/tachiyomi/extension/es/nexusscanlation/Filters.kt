package eu.kanade.tachiyomi.extension.es.nexusscanlation

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(
    default: String = "popular",
) : SelectFilter(
    "Ordenar por",
    listOf(
        "Popular" to "popular",
        "Nuevo" to "nuevo",
        "A–Z" to "az",
        "Rating" to "rating",
    ),
    default,
)

class StatusFilter :
    SelectFilter(
        "Estado",
        listOf(
            "Todos" to "",
            "En Emisión" to "en_emision",
            "Completado" to "finalizado",
            "En Pausa" to "pausado",
            "Cancelado" to "cancelado",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Todos" to "",
            "Manhwa" to "manhwa",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Novela" to "novel",
            "Manfra" to "manfra",
            "Doujin" to "doujin",
        ),
    )

class GenreFilter(genres: List<Pair<String, String>>) :
    SelectFilter(
        "Género",
        genres,
    )

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    default: String = options.first().second,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == default }.coerceAtLeast(0),
) {
    fun selectedValue(): String = options[state].second
}
