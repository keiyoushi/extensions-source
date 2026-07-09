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
)

@Serializable
internal class RecommendCategoryDto(
    val name: String,
    val categoryTypeName: String,
    val link: String,
)

@Serializable
internal class CategoryDto(
    val name: String,
    val link: String,
)
