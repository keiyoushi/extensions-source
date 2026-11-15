package eu.kanade.tachiyomi.extension.tr.holyscans

import kotlinx.serialization.Serializable

@Serializable
class AjaxResponse(
    private val data: AjaxData? = null,
) {
    val htmlContent: String get() = data?.content ?: ""
    val hasNext: Boolean get() = data?.pagination?.contains("next page-numbers") == true
}

@Serializable
class AjaxData(
    val content: String? = null,
    val pagination: String? = null,
)

@Serializable
class LiveSearchResponse(
    val data: String = "",
)

@Serializable
class PagesResponse(
    private val data: PagesData? = null,
) {
    val urls: List<String> get() = data?.urls ?: emptyList()
}

@Serializable
class PagesData(
    val urls: List<String> = emptyList(),
)
