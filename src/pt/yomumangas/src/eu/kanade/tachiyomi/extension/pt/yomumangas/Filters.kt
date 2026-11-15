package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            Pair("Qualquer", ""),
            Pair("Mangá", "MANGA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
            Pair("Novel", "NOVEL"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("Qualquer", ""),
            Pair("Em andamento", "ONGOING"),
            Pair("Completo", "COMPLETE"),
            Pair("Hiato", "HIATUS"),
            Pair("Cancelado", "CANCELLED"),
        ),
    )

class NsfwFilter :
    UriPartFilter(
        "NSFW",
        arrayOf(
            Pair("Qualquer", ""),
            Pair("Ocultar", "false"),
            Pair("Mostrar", "true"),
        ),
    )

class Genre(name: String, val id: Int) : Filter.CheckBox(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Gêneros", genres.sortedBy { it.name })

class Tag(name: String, val id: Int) : Filter.CheckBox(name)
class TagFilter(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags.sortedBy { it.name })
