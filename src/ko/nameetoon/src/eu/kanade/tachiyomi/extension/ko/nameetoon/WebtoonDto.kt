package eu.kanade.tachiyomi.extension.ko.nameetoon

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class WebtoonDto(
    @SerialName("wt_idx") val id: String,
    @SerialName("wt_title") private val title: String,
    @SerialName("wt_thumb") private val thumbnail: String? = null,
    @SerialName("wt_author") val author: String? = null,
    @SerialName("wt_company") val company: String? = null,
    @SerialName("wt_fin") private val progress: String? = null,
) {
    val status get() = when (progress) {
        "연재중" -> SManga.ONGOING
        "완결" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = this@WebtoonDto.title
        thumbnail_url = thumbnail?.let { "$baseUrl/img/data/webtoon/$it" }
    }
}
