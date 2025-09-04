package eu.kanade.tachiyomi.extension.es.lectorjpg

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SeriesQueryDto(
    val data: List<SeriesDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
) {
    fun hasNextPage() = nextCursor != null
}

@Serializable
class SeriesDto(
    private val name: String,
    private val slug: String,
    @SerialName("cover_url") private val cover: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = slug
        thumbnail_url = cover
    }
}
