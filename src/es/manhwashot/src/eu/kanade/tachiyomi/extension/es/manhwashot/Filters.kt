package eu.kanade.tachiyomi.extension.es.manhwashot

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter(
        "Tipo",
        listOf(
            "Todos" to null,
            "+18" to "adult",
            "BL" to "bl",
            "Shoujo" to "shoujo",
            "Manga" to "manga",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Estado",
        listOf(
            "Cualquier estado" to null,
            "En emisión" to "on-going",
            "Completado" to "end",
            "En pausa" to "on-hold",
            "Cancelado" to "canceled",
            "Próximamente" to "upcoming",
        ),
    )

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreCheckBox>("Géneros", genres.map { GenreCheckBox(it.name, it.slug) }) {
    val checked get() = state.filter { it.state }.map { it.slug }.ifEmpty { null }
}
