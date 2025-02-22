package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ChapterListDto = PaginatedResponseDto<ChapterDataDto>

typealias ChapterDto = ResponseDto<ChapterDataDto>

@Serializable
@SerialName(MDConstants.chapter)
data class ChapterDataDto(override val attributes: ChapterAttributesDto? = null) : EntityDto()

@Serializable
data class ChapterAttributesDto(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val pages: Int,
    val publishAt: String,
    val externalUrl: String?,
) : AttributesDto() {

    /**
     * Returns true if the chapter is from an external website and have no pages.
     */
    val isInvalid: Boolean
        get() = externalUrl != null && pages == 0
}
