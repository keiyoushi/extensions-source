package eu.kanade.tachiyomi.extension.es.onfmangas

import eu.kanade.tachiyomi.source.model.Filter

class TabFilter : Filter.Select<String>("Categoría principal", tabs.map { it.first }.toTypedArray()) {
    val selected get() = tabs[state].second

    companion object {
        private val tabs = arrayOf(
            "General" to "general",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "BL / Yaoi" to "yaoi",
            "GL / Yuri" to "yuri",
        )
    }
}

class GenreOption(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreOption>("Géneros", genres.map { GenreOption(it.name, it.id) })
