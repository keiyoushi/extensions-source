package eu.kanade.tachiyomi.extension.es.lectortmo

import eu.kanade.tachiyomi.source.model.Filter

class ContentType(name: String, val id: String) : Filter.TriState(name)

class ContentTypeList(content: List<ContentType>) : Filter.Group<ContentType>("Filtrar por tipo de contenido", content)

class Genre(name: String, val id: String) : Filter.TriState(name)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Filtrar por géneros", genres)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
