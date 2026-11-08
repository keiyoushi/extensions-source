package eu.kanade.tachiyomi.extension.pt.instahentai

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second.takeIf { it.isNotEmpty() }
}

class CategoryFilter :
    UriPartFilter(
        "Categoria",
        arrayOf(
            "Todas" to "",
            "Pornhwa" to "pornhwa",
            "Doujinshi" to "doujinshi",
            "H-Manga" to "h-manga",
            "HQ" to "hq",
            "Manhua" to "manhua",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "Todos" to "",
            "Ativo" to "ativo",
            "Completo" to "completo",
        ),
    )

class ColorFilter :
    UriPartFilter(
        "Cor",
        arrayOf(
            "Todas" to "",
            "Colorido" to "colorido",
            "Preto e branco" to "preto-e-branco",
        ),
    )

class GenreOption(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreOption>("Gêneros", genres.map { GenreOption(it.name, it.slug) })
