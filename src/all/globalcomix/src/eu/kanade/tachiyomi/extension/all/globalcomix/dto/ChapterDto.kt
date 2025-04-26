package eu.kanade.tachiyomi.extension.all.globalcomix.dto

import eu.kanade.tachiyomi.extension.all.globalcomix.GlobalComix.Companion.dateFormatter
import eu.kanade.tachiyomi.extension.all.globalcomix.GlobalComixConstants
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ChapterDto = ResponseDto<ChapterDataDto>
typealias ChaptersDto = PaginatedResponseDto<ChapterDataDto>

@Suppress("PropertyName")
@Serializable
@SerialName(GlobalComixConstants.release)
class ChapterDataDto(
    val title: String,
    val chapter: String, // Stringified number
    val page_count: Int,
    val free_page_count: Int,
    val key: String, // UUID, required for /readV2 endpoint
    val premium_only: Int,
    val published_time: String,

    // Only available when calling the /readV2 endpoint
    val page_objects: List<PageDataDto>?,
) : EntityDto() {
    val isPremium: Boolean
        get() = premium_only == 1 || free_page_count < page_count - 1

    companion object {
        /**
         * Create an [SChapter] instance from the JSON DTO element.
         */
        fun ChapterDataDto.createChapter(): SChapter {
            val chapterName = mutableListOf<String>()
            if (isPremium) {
                chapterName.add(GlobalComixConstants.lockSymbol)
            }

            chapter.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Ch.$it")
                }
            }

            title.let {
                if (it.isNotEmpty()) {
                    if (chapterName.isNotEmpty()) {
                        chapterName.add("-")
                    }
                    chapterName.add(it)
                }
            }

            return SChapter.create().apply {
                url = key
                name = chapterName.joinToString(" ")
                date_upload = dateFormatter.parse(published_time)?.time ?: 0
            }
        }
    }
}
