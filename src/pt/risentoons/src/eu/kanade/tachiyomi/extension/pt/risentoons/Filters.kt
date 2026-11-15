package eu.kanade.tachiyomi.extension.pt.risentoons

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        arrayOf(
            "Atualizados" to "",
            "Populares" to "views",
            "Avaliação" to "rating",
            "Título" to "title",
            "Adicionados" to "created_at",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        arrayOf(
            "Todos" to "",
            "Mangá" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "Todos" to "",
            "Em andamento" to "ongoing",
            "Completo" to "completed",
            "Hiato" to "hiatus",
            "Cancelado" to "dropped",
        ),
    )

class Genre(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) : Filter.Group<Genre>("Gêneros", genres.map(::Genre))
