package eu.kanade.tachiyomi.extension.pt.lapkzone

import eu.kanade.tachiyomi.source.model.Filter
import org.jsoup.parser.Parser

class CategoryOption(name: String, val id: Int) : Filter.TriState(name)

class CategoryFilter(categories: List<CategoryDto>) :
    Filter.Group<CategoryOption>(
        "Categorias",
        categories.map { CategoryOption(Parser.unescapeEntities(it.name, false), it.id) },
    ) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}
