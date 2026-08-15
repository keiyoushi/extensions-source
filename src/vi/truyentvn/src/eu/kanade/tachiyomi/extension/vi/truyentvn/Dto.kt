package eu.kanade.tachiyomi.extension.vi.truyentvn

import kotlinx.serialization.Serializable

@Serializable
class SearchAjaxResponseDto(
    private val success: Boolean,
    private val data: SearchAjaxDataDto? = null,
) {
    fun series(): List<SearchSeriesDto> {
        if (!success) return emptyList()
        return data?.series().orEmpty()
    }
}

@Serializable
class SearchAjaxDataDto(
    private val series: List<SearchSeriesDto>? = null,
) {
    fun series(): List<SearchSeriesDto>? = series
}

@Serializable
class SearchSeriesDto(
    private val title: String,
    private val url: String,
    private val thumbnail: String? = null,
) {
    fun title(): String = title

    fun url(): String = url

    fun thumbnail(): String? = thumbnail
}

@Serializable
class ChaptersAjaxResponseDto(
    private val success: Boolean,
    private val data: ChaptersAjaxDataDto? = null,
) {
    fun data(): ChaptersAjaxDataDto? {
        if (!success) return null
        return data
    }
}

@Serializable
class ChaptersAjaxDataDto(
    private val html: String? = null,
    private val pagination: String? = null,
) {
    fun html(): String? = html

    fun pagination(): String? = pagination
}
