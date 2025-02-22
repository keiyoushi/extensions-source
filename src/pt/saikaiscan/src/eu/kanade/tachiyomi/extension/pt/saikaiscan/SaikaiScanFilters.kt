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

data class Country(val name: String, val id: Int) {
    override fun toString(): String = name
}

open class EnhancedSelect<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
    val selected: T
        get() = values[state]
}

class CountryFilter(countries: List<Country>) :
    EnhancedSelect<Country>("Nacionalidade", countries.toTypedArray()),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state > 0) {
            url.addQueryParameter("country", selected.id.toString())
        }
    }
}

data class Status(val name: String, val id: Int) {
    override fun toString(): String = name
}

class StatusFilter(statuses: List<Status>) :
    EnhancedSelect<Status>("Status", statuses.toTypedArray()),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state > 0) {
            url.addQueryParameter("status", selected.id.toString())
        }
    }
}

data class SortProperty(val name: String, val slug: String) {
    override fun toString(): String = name
}

class SortByFilter(val sortProperties: List<SortProperty>) :
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
