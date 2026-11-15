package eu.kanade.tachiyomi.extension.th.nekohentai

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(private val categories: List<String>) : Filter.Select<String>("หมวดหมู่", (listOf("ทั้งหมด") + categories).toTypedArray()) {
    val value get() = categories.getOrNull(state - 1)
}

class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreDto>) : Filter.Group<GenreCheckBox>("ประเภท", genres.map { GenreCheckBox(it.name, it.id) })
