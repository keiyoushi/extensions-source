package eu.kanade.tachiyomi.extension.fr.japscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class SearchResultDto(
    private val url: String,
    private val name: String,
    private val image: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = this@SearchResultDto.url
        title = name
        thumbnail_url = image?.let { baseUrl + it }
    }
}
