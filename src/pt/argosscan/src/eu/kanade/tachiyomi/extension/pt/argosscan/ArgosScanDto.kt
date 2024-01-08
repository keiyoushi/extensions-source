package eu.kanade.tachiyomi.extension.pt.argosscan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArgosResponseDto<T>(
    val data: Map<String, T>? = null,
)

@Serializable
data class ArgosProjectListDto(
    val count: Int = 0,
    val currentPage: Int = 0,
    val limit: Int = 0,
    val projects: List<ArgosProjectDto> = emptyList(),
    val totalPages: Int = 0,
)

@Serializable
data class ArgosProjectDto(
    val adult: Boolean? = false,
    val alternative: List<String>? = emptyList(),
    val authors: List<String>? = emptyList(),
    val cover: String? = "",
    @SerialName("getChapters") val chapters: List<ArgosChapterDto> = emptyList(),
    val description: String? = "",
    val id: Int = 0,
    val name: String? = "",
    @SerialName("getTags") val tags: List<ArgosTagDto>? = emptyList(),
    val type: String? = "",
)

@Serializable
data class ArgosChapterDto(
    val createAt: String? = "",
    val id: String = "",
    val images: List<String>? = emptyList(),
    val number: Int? = 0,
    val project: ArgosProjectDto? = null,
    val title: String? = "",
)

@Serializable
data class ArgosTagDto(
    val name: String,
)
