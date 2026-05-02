package eu.kanade.tachiyomi.extension.zh.iqiyi

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    private val data: DataDto? = null,
) {
    fun toChapterList(): List<SChapter> = data?.episodes?.map { it.toSChapter() }?.reversed() ?: emptyList()

    @Serializable
    class DataDto(
        val episodes: List<EpisodeDto>? = null,
    )

    @Serializable
    class EpisodeDto(
        private val comicId: String,
        private val episodeId: String,
        private val episodeTitle: String,
        private val episodeOrder: Int,
        private val firstOnlineTime: Long,
    ) {
        fun toSChapter() = SChapter.create().apply {
            url = "/reader/${comicId}_$episodeId.html"
            name = "$episodeOrder $episodeTitle"
            date_upload = firstOnlineTime
        }
    }
}
