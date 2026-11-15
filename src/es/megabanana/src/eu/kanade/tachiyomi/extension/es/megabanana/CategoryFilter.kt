package eu.kanade.tachiyomi.extension.es.megabanana

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(private val categories: List<Pair<String, String>>) : Filter.Select<String>("Categoría", categories.map { it.first }.toTypedArray()) {
    val slug get() = categories[state].second.ifEmpty { null }
}
