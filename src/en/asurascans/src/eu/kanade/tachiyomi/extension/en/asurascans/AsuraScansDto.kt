package eu.kanade.tachiyomi.extension.en.asurascans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class FiltersDto(
    val genres: List<FilterItemDto>,
    val statuses: List<FilterItemDto>,
    val types: List<FilterItemDto>,
)

@Serializable
class FilterItemDto(
    val id: Int,
    val name: String,
)

@Serializable
class PageDto(
    val order: Int,
    val url: String = "",
    val id: Int = 0,
)

@Serializable
class ChapterDataDto(
    val id: Int,
    @SerialName("is_early_access") val isEarlyAccess: Boolean = false,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class UnlockResponseDto(
    val success: Boolean,
    val data: UnlockDataDto,
)

@Serializable
class UnlockDataDto(
    val id: Int,
    @SerialName("unlock_token") val unlockToken: String,
    @SerialName("is_early_access") val isEarlyAccess: Boolean,
    val pages: List<PageDto>,
)

@Serializable
class MediaResponseDto(
    val data: String,
    @SerialName("content-type") val contentType: String = "",
)

@Serializable
class UnlockRequestDto(
    val chapterId: Int,
)

@Serializable
class MediaRequestDto(
    @SerialName("media_id")
    val mediaId: Int,
    @SerialName("chapter_id")
    val chapterId: Int,
    val token: String,
    val quality: String,
)
