package eu.kanade.tachiyomi.extension.pt.megahentai

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter(
        "Tipo",
        "t",
        listOf("Todos" to "", "Manhwa" to "Manhwa", "H-Manga" to "H-Manga", "Doujinshi" to "Doujinshi", "HQs" to "HQ"),
    )

class StatusFilter : SelectFilter("Status", "status", listOf("Todos" to "", "Ativo" to "Ativo", "Completo" to "Completo"))

class GenreFilter(genres: List<String>) : SelectFilter("Gênero", "genero", listOf("Todos" to "") + genres.map { it to it })

class OrderFilter :
    SelectFilter(
        "Ordenar",
        "order",
        listOf("Views" to "views", "Mais Recente" to "new", "Mais Antigo" to "old"),
    )
