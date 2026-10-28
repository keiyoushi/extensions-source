package eu.kanade.tachiyomi.extension.es.skymangas

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

class SortFilter(state: Int = 0) :
    Filter.Select<String>("Ordenar por", SORTS.map { it.first }.toTypedArray(), state),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        url.addQueryParameter("sort", SORTS[state].second)
    }

    companion object {
        private val SORTS = listOf(
            "Populares" to "popular",
            "Recientes" to "latest",
            "Actualizados" to "updated",
            "Mejor valorados" to "rating",
            "Más seguidos" to "follows",
            "Más capítulos" to "chapters",
            "A-Z" to "alpha",
        )
        const val UPDATED = 2
    }
}

class AvailabilityFilter :
    Filter.Select<String>("Disponibilidad", OPTIONS.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        url.addQueryParameter("availability", OPTIONS[state].second)
    }

    companion object {
        private val OPTIONS = listOf(
            "Con capítulos" to "readable",
            "Todos" to "all",
            "Sin capítulos" to "catalog_only",
        )
    }
}

class SelectFilter(name: String, private val param: String, private val options: List<Tag>) :
    Filter.Select<String>(name, (listOf("Todos") + options.map { it.name }).toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        if (state > 0) url.addQueryParameter(param, options[state - 1].id.toString())
    }
}

class CheckBoxOption(name: String, val id: Int) : Filter.CheckBox(name)

class CheckBoxGroup(name: String, private val param: String, options: List<Tag>) :
    Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.name, it.id) }),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.id }
        if (checked.isNotEmpty()) url.addQueryParameter(param, checked.joinToString(","))
    }
}

class NumberFilter(name: String, private val param: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        state.trim().toIntOrNull()?.let { url.addQueryParameter(param, it.toString()) }
    }
}
