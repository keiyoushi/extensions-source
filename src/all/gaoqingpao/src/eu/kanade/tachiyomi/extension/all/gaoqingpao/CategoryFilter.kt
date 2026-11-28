package eu.kanade.tachiyomi.extension.all.gaoqingpao

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(private val categories: List<Pair<String, Int>>) : Filter.Select<String>("分类", arrayOf("全部") + categories.map { it.first }) {
    val id get() = categories.getOrNull(state - 1)?.second
}
