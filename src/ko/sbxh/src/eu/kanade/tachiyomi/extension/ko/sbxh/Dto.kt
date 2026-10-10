package eu.kanade.tachiyomi.extension.ko.sbxh

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class WorkListDto(
    val works: List<WorkDto>,
    val hasMore: Boolean,
)

@Serializable
class WorkDto(
    private val sourceWorkId: String,
    private val title: String,
    private val thumbnailUrl: String? = null,
) {
    fun toSManga(type: String) = SManga.create().apply {
        url = "/$type/$sourceWorkId"
        title = this@WorkDto.title
        thumbnail_url = thumbnailUrl
    }
}
