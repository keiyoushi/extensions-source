package eu.kanade.tachiyomi.extension.zh.mangabz

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator

fun getFilterListInternal(categories: List<CategoryData>): FilterList {
    val list: List<Filter<*>> = if (categories.isEmpty()) {
        listOf(Filter.Header("点击“重置”刷新分类"))
    } else {
        buildList(categories.size + 1) {
            add(Filter.Header("分类（搜索文本时无效）"))
            categories.mapTo(this, CategoryData::toFilter)
        }
    }
    return FilterList(list)
}

fun parseFilterList(filters: FilterList): String =
    filters.filterIsInstance<CategoryFilter>().joinToString("-") { it.id.toString() }

fun parseCategories(document: Document): List<CategoryData> {
    val lines = document.select(Evaluator.Class("class-line")).ifEmpty { return emptyList() }
    val defaultIds = IntArray(lines.size)
    val idArrays = arrayOfNulls<IntArray>(lines.size)

    val result = lines.mapIndexed { filterIndex, line ->
        val options = line.select(Evaluator.Tag("a")).mapIndexed { optionIndex, option ->
            val optionName = option.ownText()!!
            if (optionIndex == 0) {
                Pair(optionName, 0) // id is unknown
            } else {
                val idTuple = option.attr("href")
                    .removePrefix("/manga-list-").removeSuffix("/").split("-")
                for ((indexInTuple, id) in idTuple.withIndex()) {
                    if (indexInTuple != filterIndex) defaultIds[indexInTuple] = id.toInt()
                }
                Pair(optionName, idTuple[filterIndex].toInt())
            }
        }

        val name = line.child(0).ownText().removeSuffix("：")
        val values = Array(options.size) { options[it].first }
        val ids = IntArray(options.size) { options[it].second }
        idArrays[filterIndex] = ids
        CategoryData(name, values, ids)
    }

    for ((i, idArray) in idArrays.withIndex()) {
        idArray!![0] = defaultIds[i]
    }

    return result
}

class CategoryData(
    private val name: String,
    private val values: Array<String>,
    private val ids: IntArray,
) {
    fun toFilter() = CategoryFilter(name, values, ids)
}

class CategoryFilter(name: String, values: Array<String>, private val ids: IntArray) :
    Filter.Select<String>(name, values) {
    val id get() = ids[state]
}
