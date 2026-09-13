package eu.kanade.tachiyomi.multisrc.keyoapp

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

class Selection(name: String, val value: String) : Filter.CheckBox(name)

abstract class MultiSelectFilter(
    name: String,
    private val param: String,
    vals: Map<String, String>,
) : Filter.Group<Selection>(
    name,
    vals.map { Selection(it.key, it.value) },
) {

    fun addToUri(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }
        checked.forEach {
            builder.addQueryParameter(param, it.value)
        }
    }
}

class Genre(name: String, val id: String = name) : Filter.CheckBox(name)

class GenreFilter(
    genres: Map<String, String>,
) : MultiSelectFilter("Genres", "genre", genres)

class TypeFilter(
    types: Map<String, String>,
) : MultiSelectFilter("Type", "type", types)

class StatusFilter(
    statuses: Map<String, String>,
) : MultiSelectFilter("Status", "status", statuses)
