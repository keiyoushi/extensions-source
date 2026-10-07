package eu.kanade.tachiyomi.extension.es.lectorxd

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement

internal object Filters {

    class Type :
        Filter.Select<String>(
            "Tipo",
            arrayOf(
                "Todos",
                "Manga",
                "Manhwa",
                "Manhua",
                "One Shot",
            ),
        )

    class Status :
        Filter.Select<String>(
            "Estado",
            arrayOf(
                "Todos",
                "En emisión",
                "Completado",
            ),
        )

    class Demographic :
        Filter.Select<String>(
            "Demografía",
            arrayOf(
                "Todos",
                "Shounen",
                "Seinen",
                "Shoujo",
                "Josei",
            ),
        )

    class AdultContent :
        Filter.Select<String>(
            "Contenido +18",
            arrayOf(
                "Solo general",
                "Todo",
                "Solo +18",
            ),
        )

    class OrderBy :
        Filter.Select<String>(
            "Ordenar por",
            arrayOf(
                "Recientes",
                "Mejor valorados",
                "Más vistos",
                "Más gente leyendo",
                "Más gente por leer",
                "Más completados",
                "Más capítulos",
            ),
        )

    class Tag(
        val ids: List<String>,
        name: String,
    ) : Filter.CheckBox(name)

    class GenreGroup(
        letter: String,
        tags: List<Tag>,
    ) : Filter.Group<Tag>(
        letter,
        tags,
    )

    class GenresFilter(
        groups: List<GenreGroup>,
    ) : Filter.Group<GenreGroup>(
        "Géneros",
        groups,
    )

    val typeValues = arrayOf(
        null,
        "manga",
        "manhwa",
        "manhua",
        "one_shot",
    )

    val statusValues = arrayOf(
        null,
        "en_emision",
        "completado",
    )

    val demographicValues = arrayOf(
        null,
        "shounen",
        "seinen",
        "shoujo",
        "josei",
    )

    val adultContentValues = arrayOf(
        "safe",
        "all",
        "adult",
    )

    val orderByValues = arrayOf(
        "recent",
        "rating",
        "views",
        "leyendo",
        "por_leer",
        "completado",
        "chapters",
    )

    fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filtros de LectorXD"),
            OrderBy(),
            Status(),
            Type(),
            Demographic(),
            AdultContent(),
        )

        val genres = data?.parseAs<List<Pair<String, String>>>()
            .orEmpty()

        val groups = genres
            .groupBy { (name, _) -> name }
            .map { (name, entries) ->
                Tag(
                    ids = entries.map { it.second }.distinct(),
                    name = name,
                )
            }
            .sortedBy { it.name.lowercase() }
            .groupBy { tag ->
                when (val letter = tag.name.first().uppercaseChar()) {
                    'Á' -> "A"
                    'É' -> "E"
                    'Í' -> "I"
                    'Ó' -> "O"
                    'Ú', 'Ü' -> "U"
                    else -> letter.toString()
                }
            }
            .map { (letter, tags) ->
                GenreGroup(
                    letter = "Grupo $letter",
                    tags = tags,
                )
            }

        if (groups.isNotEmpty()) {
            filters.add(GenresFilter(groups))
        }
        return FilterList(filters)
    }
}
