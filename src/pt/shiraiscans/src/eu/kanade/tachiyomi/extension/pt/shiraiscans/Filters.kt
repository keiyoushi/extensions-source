package eu.kanade.tachiyomi.extension.pt.shiraiscans

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class GenreFilter :
    UriPartFilter(
        "Gênero",
        arrayOf(
            Pair("Todos", "todos"),
            Pair("Adulto", "Adulto"),
            Pair("Aventura", "Aventura"),
            Pair("Comédia", "Comédia"),
            Pair("Conto", "Conto"),
            Pair("Drama", "Drama"),
            Pair("Fantasia", "Fantasia"),
            Pair("Histórico", "Histórico"),
            Pair("Magia", "Magia"),
            Pair("Mistério", "Mistério"),
            Pair("Reencarnação", "Reencarnação"),
            Pair("Romance", "Romance"),
            Pair("Shoujo", "Shoujo"),
            Pair("Sobrenatural", "Sobrenatural"),
            Pair("Tragédia", "Tragédia"),
            Pair("Traição", "Traição"),
            Pair("Viagem no tempo", "Viagem no tempo"),
            Pair("Vingança", "Vingança"),
        ),
    )
