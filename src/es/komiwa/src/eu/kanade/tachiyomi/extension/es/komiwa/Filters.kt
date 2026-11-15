package eu.kanade.tachiyomi.extension.es.komiwa

import eu.kanade.tachiyomi.source.model.Filter

class CheckBoxOption(name: String, val id: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, options: List<TaxonomyDto>) : Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.name, it.id!!) }) {
    val checked get() = state.filter { it.state }.map { it.id }.ifEmpty { null }
}

class GenreFilter(options: List<TaxonomyDto>) : CheckBoxGroup("Géneros (todos)", options)

class TypeFilter(options: List<TaxonomyDto>) : CheckBoxGroup("Tipos (cualquiera)", options)

class DemographyFilter(options: List<TaxonomyDto>) : CheckBoxGroup("Demografías (cualquiera)", options)

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class StatusFilter(statuses: List<TaxonomyDto>) : SelectFilter("Estado", listOf("Todos" to null) + statuses.map { it.name to it.slug })

class AgeRatingFilter :
    SelectFilter(
        "Clasificación",
        listOf(
            "Todo el contenido" to null,
            "Sin contenido erótico" to "SAFE",
            "Solo contenido erótico" to "EROTIC",
        ),
    )

class SortFilter :
    SelectFilter(
        "Ordenar",
        listOf(
            "Más vistos" to "views-desc",
            "Mejor valorados" to "rating-desc",
            "Más recientes" to "createdAt-desc",
            "Última actualización" to "updatedAt-desc",
            "Más capítulos" to "numChapters-desc",
            "Nombre (A-Z)" to "title-asc",
            "Nombre (Z-A)" to "title-desc",
        ),
    ) {
    val sortBy get() = selected!!.substringBefore('-')
    val sortOrder get() = selected!!.substringAfter('-')
}
