package eu.kanade.tachiyomi.extension.zh.acgn

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(private val categories: List<CategoryDto>) : Filter.Select<String>("分類", (listOf("全部") + categories.map { it.name }).toTypedArray()) {
    val path get() = categories.getOrNull(state - 1)?.path
}

class LetterFilter : Filter.Select<String>("字母", (listOf("全部") + LETTERS).toTypedArray()) {
    val path get() = LETTERS.getOrNull(state - 1)?.let { "pinyin-${it.lowercase()}.htm" }
}

private val LETTERS = ('A'..'Z').map(Char::toString) + "0-9"
