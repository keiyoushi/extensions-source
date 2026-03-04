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
    val externalUrl: String?,
    val isUnavailable: Boolean = false,
) : AttributesDto() {

    /**
     * Returns true if the chapter have no pages.
     *
     * Note (03/04/2026): the API sometimes falsely reports 'pages = 0' for chapters that actually
     * have pages (e.g. 6ba0f2ef-02d7-4999-b347-c26c02ebea40). 'isInvalid' is used only as a first-pass signal.
     * Verify via MD@HOME before discarding any chapter.
     */
    val isInvalid: Boolean
        get() = pages == 0
}
