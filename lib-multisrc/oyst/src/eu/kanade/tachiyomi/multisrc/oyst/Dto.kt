package eu.kanade.tachiyomi.multisrc.oyst

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ProjectDto(
    @SerialName("l") val id: Long,
)

@Serializable
class ChapterLinksDto(
    @SerialName("m") val links: Map<String, String>,
)

@Serializable
class ReaderDto(
    @SerialName("c") val chapterId: Long,
    @SerialName("n") val pageCount: Int,
    val ttl: Int,
)

@Serializable
class PageCodesDto(
    @SerialName("k") val codes: List<PageCodeDto>? = null,
    @SerialName("cap") val captchaSiteKey: String? = null,
)

@Serializable
class PageCodeDto(
    @SerialName("i") val page: Int,
    @SerialName("c") val code: String,
)
