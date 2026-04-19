package eu.kanade.tachiyomi.extension.es.anzmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    val suggestions: List<SearchSuggestionDto> = emptyList(),
)

@Serializable
class SearchSuggestionDto(
    private val value: String,
    private val data: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = value
        url = "/manga/$data"
        thumbnail_url = "$baseUrl/uploads/manga/$data/cover/cover_250x350.jpg"
    }
}
