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
    val url: String,
)

@Serializable
class UnlockRequestDto(
    @SerialName("chapterId")
    val chapterId: Int,
)

@Serializable
class UnlockResponseDto(
    val success: Boolean,
    val data: UnlockDataDto? = null,
    val message: String? = null,
)

@Serializable
class UnlockDataDto(
    val id: Int,
    val name: Int,
    val title: String? = null,
    @SerialName("is_early_access")
    val isEarlyAccess: Boolean,
    @SerialName("unlock_token")
    val unlockToken: String? = null,
    val pages: List<UnlockPageDto> = emptyList(),
)

@Serializable
class UnlockPageDto(
    val order: Int,
    val id: Int,
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

@Serializable
class MediaResponseDto(
    val data: String,
    @SerialName("content-type")
    val contentType: String? = null,
)
