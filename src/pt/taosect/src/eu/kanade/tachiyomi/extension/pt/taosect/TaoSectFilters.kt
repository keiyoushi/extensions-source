package eu.kanade.tachiyomi.extension.pt.taosect

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface QueryParameterFilter {
    fun toQueryParameter(url: HttpUrl.Builder, query: String)
}

class Tag(val id: String, name: String) : Filter.TriState(name)

class CountryFilter(countries: List<Tag>) :
    Filter.Group<Tag>("País", countries),
    QueryParameterFilter {

    override fun toQueryParameter(url: HttpUrl.Builder, query: String) {
        state
            .groupBy { it.state }
            .entries
            .forEach { entry ->
                val values = entry.value.joinToString(",") { it.id }

                if (entry.key == TriState.STATE_EXCLUDE) {
                    url.addQueryParameter("paises_exclude", values)
                } else if (entry.key == TriState.STATE_INCLUDE) {
                    url.addQueryParameter("paises", values)
                }
            }
    }
}

class StatusFilter(status: List<Tag>) :
    Filter.Group<Tag>("Status", status),
    QueryParameterFilter {

    override fun toQueryParameter(url: HttpUrl.Builder, query: String) {
        state
            .groupBy { it.state }
            .entries
            .forEach { entry ->
                val values = entry.value.joinToString(",") { it.id }

                if (entry.key == TriState.STATE_EXCLUDE) {
                    url.addQueryParameter("situacao_exclude", values)
                } else if (entry.key == TriState.STATE_INCLUDE) {
                    url.addQueryParameter("situacao", values)
                }
            }
    }
}

class GenreFilter(genres: List<Tag>) :
    Filter.Group<Tag>("Gêneros", genres),
    QueryParameterFilter {

    override fun toQueryParameter(url: HttpUrl.Builder, query: String) {
        state
            .groupBy { it.state }
            .entries
            .forEach { entry ->
                val values = entry.value.joinToString(",") { it.id }

                if (entry.key == TriState.STATE_EXCLUDE) {
                    url.addQueryParameter("generos_exclude", values)
                } else if (entry.key == TriState.STATE_INCLUDE) {
                    url.addQueryParameter("generos", values)
                }
            }
    }
}

class SortFilter(private val sortings: List<Tag>, private val default: Int) :
    Filter.Sort(
        "Ordem",
        sortings.map { it.name }.toTypedArray(),
        Selection(default, false),
    ),
    QueryParameterFilter {

    override fun toQueryParameter(url: HttpUrl.Builder, query: String) {
        val orderBy = if (state == null) {
            sortings[default].id
        } else {
            sortings[state!!.index].id
        }
        val order = if (state?.ascending == true) "asc" else "desc"

        url.addQueryParameter("order", order)
        url.addQueryParameter("orderby", orderBy)
    }
}

class FeaturedFilter : Filter.TriState("Mostrar destaques"), QueryParameterFilter {

    override fun toQueryParameter(url: HttpUrl.Builder, query: String) {
        if (query.isEmpty()) {
            if (state == STATE_INCLUDE) {
                url.addQueryParameter("destaque", "1")
            } else if (state == STATE_EXCLUDE) {
                url.addQueryParameter("destaque", "0")
            }
        }
    }
}

class NsfwFilter : Filter.TriState("Mostrar conteúdo +18"), QueryParameterFilter {

    override fun toQueryParameter(url: HttpUrl.Builder, query: String) {
        if (state == STATE_INCLUDE) {
            url.addQueryParameter("mais_18", "1")
        } else if (state == STATE_EXCLUDE) {
            url.addQueryParameter("mais_18", "0")
        }
    }
}
