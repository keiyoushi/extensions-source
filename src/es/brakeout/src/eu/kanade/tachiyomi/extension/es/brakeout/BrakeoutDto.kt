package eu.kanade.tachiyomi.extension.es.brakeout

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TopSeriesPayloadDto(
    @SerialName("diario") val data: List<TopSeriesDto>,
)

@Serializable
class TopSeriesDto(
    val project: SeriesDto,
)

@Serializable
class SeriesDto(
    @SerialName("nombre") val name: String,
    @SerialName("portada") private val thumbnail: String,
    private val slug: String,
    private val id: Int,
) {
    fun toSManga() = SManga.create().apply {
        thumbnail_url = thumbnail
        title = name
        url = "/ver/$id/$slug"
    }
}
