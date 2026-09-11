package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ChapterListDto = PaginatedResponseDto<ChapterDataDto>

typealias ChapterDto = ResponseDto<ChapterDataDto>

@Serializable
@SerialName(MDConstants.CHAPTER)
data class ChapterDataDto(override val attributes: ChapterAttributesDto? = null) : EntityDto()

@Serializable
data class ChapterAttributesDto(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val pages: Int,
    val publishAt: String,
    val updatedAt: String,
    val readableAt: String,
    val externalUrl: String?,
    val isUnavailable: Boolean = false,
) : AttributesDto() {

    /**
     * There are two cases where this property returns true:
     * 1. The chapter is from an external website and has no pages
     * 2. The chapter is from an external website, has only one page, and was updated after it was readable
     *
     * In the second case, the external chapter is removed and is replaced with a single page stating that the chapter is removed.
     */
    val isInvalid: Boolean
        get() = externalUrl != null && (pages == 0 || (pages == 1 && updatedAt != readableAt))
}
