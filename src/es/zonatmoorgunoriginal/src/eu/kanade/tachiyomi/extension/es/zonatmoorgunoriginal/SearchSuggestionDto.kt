package eu.kanade.tachiyomi.extension.es.zonatmoorgunoriginal

import kotlinx.serialization.Serializable

@Serializable
class SearchSuggestionDto(
    private val title: String? = null,
    private val url: String? = null,
    private val type: String? = null,
) {
    fun mangaUrl(
        expectedTitle: String,
        expectedType: String?,
    ): String? = url?.takeIf {
        title?.trim()?.equals(expectedTitle.trim(), ignoreCase = true) == true &&
            (expectedType == null || type?.equals(expectedType, ignoreCase = true) == true) &&
            "/library/" in it
    }
}
