package eu.kanade.tachiyomi.extension.es.zonatmoorgunoriginal

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class SearchByFilter :
    SelectFilter(
        "Buscar por",
        listOf("Título" to "title", "Autor" to "author", "Compañía" to "company"),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Ver todo" to "",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Manhwa" to "manhwa",
            "Webtoon" to "webtoon",
            "Novela" to "novel",
            "Comic" to "comic",
            "One shot" to "one_shot",
            "Doujinshi" to "doujinshi",
            "OEL" to "oel",
        ),
    )

class DemographyFilter :
    SelectFilter(
        "Demografía",
        listOf(
            "Ver todo" to "",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Josei" to "josei",
            "Kodomo" to "kodomo",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Estado",
        listOf(
            "Ver todo" to "",
            "En emisión" to "ongoing",
            "Completado" to "completed",
            "Finalizado" to "ended",
            "En pausa" to "hiatus",
            "Cancelado" to "cancelled",
        ),
    )

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        SORT_OPTIONS.map { it.first }.toTypedArray(),
        Selection(0, false),
    ) {
    val sort get() = SORT_OPTIONS[state!!.index].second
    val dir get() = if (state!!.ascending) "asc" else "desc"

    companion object {
        private val SORT_OPTIONS = listOf(
            "Me gusta" to "likes_count",
            "Alfabético" to "alphabetically",
            "Puntuación" to "score",
            "Creación" to "creation",
            "Fecha estreno" to "release_date",
            "Núm. capítulos" to "num_chapters",
        )
    }
}

class GenreOption(
    name: String,
    val id: String,
) : Filter.TriState(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreOption>("Géneros", genres.map { GenreOption(it.name, it.id) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}
