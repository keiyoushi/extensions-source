package eu.kanade.tachiyomi.extension.all.namicomi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ChapterListDto = PaginatedResponseDto<ChapterDataDto>

@Serializable
@SerialName("chapter")
class ChapterDataDto(override val attributes: ChapterAttributesDto? = null) : EntityDto()

@Serializable
class ChapterAttributesDto(
    val name: String?,
    val volume: String?,
    val chapter: String?,
    val publishAt: String,
) : AttributesDto
