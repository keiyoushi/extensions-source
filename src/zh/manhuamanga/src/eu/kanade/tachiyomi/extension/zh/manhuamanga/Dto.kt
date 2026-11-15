package eu.kanade.tachiyomi.extension.zh.manhuamanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LinkedDataDto(
    @SerialName("@type") private val type: String? = null,
    private val itemListElement: List<BreadcrumbItemDto> = emptyList(),
) {
    // A chapter page's breadcrumb: site, series, chapter
    val seriesUrl: String?
        get() = if (type == "BreadcrumbList") itemListElement.getOrNull(1)?.item else null
}

@Serializable
class BreadcrumbItemDto(val item: String)
