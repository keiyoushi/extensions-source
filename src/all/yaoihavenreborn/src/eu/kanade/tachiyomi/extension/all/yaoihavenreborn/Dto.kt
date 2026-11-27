package eu.kanade.tachiyomi.extension.all.yaoihavenreborn

import kotlinx.serialization.Serializable

@Serializable
class CategoriesDto(val results: List<CategoryDto>)

@Serializable
class CategoryDto(val name: String, private val tags: List<TagDto>) {
    val tagNames get() = tags.map { it.name }
}

@Serializable
class TagDto(val name: String)
