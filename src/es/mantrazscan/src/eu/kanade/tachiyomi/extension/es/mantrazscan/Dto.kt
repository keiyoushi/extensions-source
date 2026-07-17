package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

private fun String.toAbsoluteUrl(baseUrl: String) = if (startsWith("/")) baseUrl + this else this

@Serializable
class SearchResponse(
    val results: List<SearchResultDto> = emptyList(),
)

@Serializable
class SearchResultDto(
    val postId: Int = 0,
    val title: String,
    val slug: String,
    val cover: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@SearchResultDto.title
        thumbnail_url = cover?.toAbsoluteUrl(baseUrl)
    }
}
