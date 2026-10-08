package eu.kanade.tachiyomi.extension.pt.saikaiscan

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlQueryFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class Genre(title: String, val id: Int) : Filter.CheckBox(title)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>("Gêneros", genres),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        val genresParameter = state
            .filter { it.state }
            .joinToString(",") { it.id.toString() }

        url.addQueryParameter("genres", genresParameter)
    }
}

class Country(val name: String, val id: Int) {
    override fun toString() = name
}

open class EnhancedSelect<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
    val selected: T
        get() = values[state]
}

class CountryFilter :
    EnhancedSelect<Country>("Nacionalidade", countryList),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state > 0) {
            url.addQueryParameter("country", selected.id.toString())
        }
    }
}

class Status(val name: String, val id: Int) {
    override fun toString() = name
}

class StatusFilter :
    EnhancedSelect<Status>("Status", statusList),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state > 0) {
            url.addQueryParameter("status", selected.id.toString())
        }
    }
}

class SortProperty(val name: String, val slug: String)

class SortByFilter :
    Filter.Sort(
        name = "Ordenar por",
        values = sortProperties.map { it.name }.toTypedArray(),
        state = Selection(2, ascending = false),
    ),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        val sortProperty = sortProperties[state!!.index]
        val sortDirection = if (state!!.ascending) "asc" else "desc"
        url.setQueryParameter("sortProperty", sortProperty.slug)
        url.setQueryParameter("sortDirection", sortDirection)
    }
}

internal val countryList = arrayOf(
    Country("Todas", 0),
    Country("Brasil", 32),
    Country("China", 45),
    Country("Coréia do Sul", 115),
    Country("Espanha", 199),
    Country("Estados Unidos da América", 1),
    Country("Japão", 109),
    Country("Portugal", 173),
)

internal val statusList = arrayOf(
    Status("Todos", 0),
    Status("Cancelado", 5),
    Status("Concluído", 1),
    Status("Dropado", 6),
    Status("Em Andamento", 2),
    Status("Hiato", 4),
    Status("Pausado", 3),
)

internal val sortProperties = listOf(
    SortProperty("Título", "title"),
    SortProperty("Quantidade de capítulos", "releases_count"),
    SortProperty("Visualizações", "pageviews"),
    SortProperty("Data de criação", "created_at"),
)
