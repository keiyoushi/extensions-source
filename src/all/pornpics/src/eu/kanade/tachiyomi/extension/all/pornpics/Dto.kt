package eu.kanade.tachiyomi.extension.all.pornpics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class MangaDto(
    private val desc: String,
    @SerialName("g_url")
    val url: String,
    @SerialName("t_url")
    val thumbnailUrl: String,
) {
    // search results append the gallery id to the description
    val title: String
        get() {
            val id = url.trimEnd('/').substringAfterLast('-').takeIf { it.all(Char::isDigit) }.orEmpty()
            return desc.removeSuffix(id).trim().ifEmpty {
                url.slugToTitle()
            }
        }
}

@Serializable
internal class FilterData(
    val categories: List<CategoryDto>,
    val tags: List<CategoryDto>,
    val pornStars: List<CategoryDto>,
    val channels: List<CategoryDto>,
)

@Serializable
internal class CategoryDto(
    val name: String,
    val link: String,
)

private fun String.slugToTitle(): String = trimEnd('/')
    .substringAfterLast('/')
    .replaceFirstChar(Char::uppercaseChar)
    .split('-')
    .filterNot { it.all(Char::isDigit) }
    .joinToString(" ")
