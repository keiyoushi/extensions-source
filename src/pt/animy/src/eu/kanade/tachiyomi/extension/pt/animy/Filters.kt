package eu.kanade.tachiyomi.extension.pt.animy

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = vals[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter(param, value)
        }
    }
}

class TypeFilter :
    UriPartFilter(
        "Tipo",
        "type",
        arrayOf(
            "Todos" to "",
            "Mangá" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            "Todos" to "",
            "Em andamento" to "ongoing",
            "Completo" to "completed",
            "Em hiato" to "hiatus",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Gênero",
        "genre",
        arrayOf(
            "Todos" to "",
            "Ação" to "Ação",
            "Aventura" to "Aventura",
            "Comédia" to "Comédia",
            "Drama" to "Drama",
            "Fantasia" to "Fantasia",
            "Romance" to "Romance",
            "Isekai" to "Isekai",
            "Terror" to "Terror",
            "Mistério" to "Mistério",
            "Psicológico" to "Psicológico",
            "Slice of Life" to "Slice of Life",
            "Sobrenatural" to "Sobrenatural",
            "Esportes" to "Esportes",
            "Mature" to "Mature",
            "Ecchi" to "Ecchi",
        ),
    )
