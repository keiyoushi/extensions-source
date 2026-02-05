package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlQueryFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class NsfwContentFilter :
    Filter.CheckBox("Conteúdo NSFW"),
    UrlQueryFilter {
    override fun addQueryParameter(url: HttpUrl.Builder) {
        if (state) {
            url.addQueryParameter("nsfw", "true")
        }
    }
}

class AdultContentFilter :
    Filter.CheckBox("Conteúdo adulto"),
    UrlQueryFilter {
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

val genresList: List<Genre> = listOf(
    Genre("Ação", "1"),
    Genre("Aventura", "8"),
    Genre("Comédia", "2"),
    Genre("Drama", "3"),
    Genre("Ecchi", "15"),
    Genre("Esportes", "14"),
    Genre("Fantasia", "6"),
    Genre("Hentai", "19"),
    Genre("Horror", "4"),
    Genre("Mahou shoujo", "18"),
    Genre("Mecha", "17"),
    Genre("Mistério", "7"),
    Genre("Música", "16"),
    Genre("Psicológico", "9"),
    Genre("Romance", "13"),
    Genre("Sci-fi", "11"),
    Genre("Slice of life", "10"),
    Genre("Sobrenatural", "5"),
    Genre("Suspense", "12"),
)

val statusList: List<Status> = listOf(
    Status("Todos", ""),
    Status("Finalizado", "COMPLETE"),
    Status("Em lançando", "ONGOING"),
    Status("Hiato", "HIATUS"),
    Status("Pausado", "ONHOLD"),
    Status("Planejado", "PLANNED"),
    Status("Arquivado", "ARCHIVED"),
    Status("Cancelado", "CANCELLED"),
)

val typesList: List<Type> = listOf(
    Type("Todos", ""),
    Type("Mangá", "MANGA"),
    Type("Manhwa", "MANHWA"),
    Type("Manhua", "MANHUA"),
    Type("One-shot", "ONESHOT"),
    Type("Doujinshi", "DOUJINSHI"),
    Type("Outros", "OTHER"),
)
