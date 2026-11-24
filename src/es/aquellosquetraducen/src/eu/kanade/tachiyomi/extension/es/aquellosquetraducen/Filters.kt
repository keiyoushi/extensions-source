package eu.kanade.tachiyomi.extension.es.aquellosquetraducen

import eu.kanade.tachiyomi.source.model.Filter

class OptionFilter(name: String, val value: String = name) : Filter.CheckBox(name)

open class OptionGroupFilter(name: String, options: List<OptionFilter>) : Filter.Group<OptionFilter>(name, options) {
    val selected get() = state.filter { it.state }.map { it.value }
}

class TypeFilter :
    OptionGroupFilter(
        "Tipo de Obra",
        listOf("Manhwa", "Manhua", "Manga", "Manga a color").map { OptionFilter(it) },
    )

class GenreFilter(genres: List<String>) : OptionGroupFilter("Género", genres.map { OptionFilter(it) })

class StatusFilter :
    OptionGroupFilter(
        "Estado",
        listOf(
            OptionFilter("En Emisión", "emision"),
            OptionFilter("En Pausa", "pausa"),
            OptionFilter("Finalizado", "finalizado"),
        ),
    )
