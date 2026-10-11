package eu.kanade.tachiyomi.extension.all.pornpics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class MangaDto(
    val desc: String,
    @SerialName("g_url")
    val url: String,
    @SerialName("t_url")
    val thumbnailUrl: String,
) {
    val title: String
        get() = desc.ifEmpty {
            url.slugToTitle()
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
