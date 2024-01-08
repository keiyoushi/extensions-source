package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlQueryFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class NsfwContentFilter : Filter.CheckBox("Conteúdo NSFW"), UrlQueryFilter {
    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state) {
            url.addQueryParameter("nsfw", "true")
        }
    }
}

class AdultContentFilter : Filter.CheckBox("Conteúdo adulto"), UrlQueryFilter {
    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state) {
            url.addQueryParameter("hentai", "true")
        }
    }
}

open class EnhancedSelect<T>(name: String, values: Array<T>) : Filter.Select<T>(name, values) {
    val selected: T
        get() = values[state]
}

data class Status(val name: String, val value: String) {
    override fun toString() = name
}

class StatusFilter(statusList: List<Status>) :
    EnhancedSelect<Status>("Status", statusList.toTypedArray()),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state > 0) {
            url.addQueryParameter("status", selected.value)
        }
    }
}

data class Type(val name: String, val value: String) {
    override fun toString() = name
}

class TypeFilter(typesList: List<Type>) :
    EnhancedSelect<Type>("Tipo", typesList.toTypedArray()),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state > 0) {
            url.addQueryParameter("type", selected.value)
        }
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name) {
    override fun toString() = name
}

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>("Gêneros", genres),
    UrlQueryFilter {

    override fun addQueryParameter(url: HttpUrl.Builder) {
        state.filter(Genre::state)
            .forEach { url.addQueryParameter("genres[]", it.id) }
    }
}
