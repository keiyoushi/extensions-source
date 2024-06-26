package eu.kanade.tachiyomi.extension.pt.slimeread.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PopularMangaDto(
    @SerialName("book_image") val thumbnail_url: String?,
    @SerialName("book_id") val id: Int,
    @SerialName("book_name_original") val name: String,
)

@Serializable
data class LatestResponseDto(
    val pages: Int,
    val page: Int,
    val data: List<PopularMangaDto>,
)

fun List<PopularMangaDto>.toSMangaList(): List<SManga> = map { item ->
    SManga.create().apply {
        thumbnail_url = item.thumbnail_url
        title = item.name
        url = "/book/${item.id}"
    }
}

@Serializable
data class MangaInfoDto(
    @SerialName("book_id") val id: Int,
    @SerialName("book_image") val thumbnail_url: String?,
    @SerialName("book_name_original") val name: String,
    @SerialName("book_status") val status: Int,
    @SerialName("book_synopsis") val description: String?,
    @SerialName("book_categories") private val _categories: List<CategoryDto>,
) {
    @Serializable
    data class CategoryDto(val categories: CatDto)

    @Serializable
    data class CatDto(@SerialName("cat_name_ptBR") val name: String)

    val categories = _categories.map { it.categories.name }
}

@Serializable
data class ChapterDto(
    @SerialName("btc_cap") val number: Float,
    val scan: ScanDto?,
) {
    @Serializable
    data class ScanDto(val scan_name: String?)
}

@Serializable
data class PageListDto(@SerialName("book_temp_cap_unit") val pages: List<PageDto>)

@Serializable
data class PageDto(
    @SerialName("btcu_image") private val path: String,
    @SerialName("btcu_provider_host") private val hostId: Int?,
) {
    val url by lazy {
        val baseUrl = when (hostId) {
            2 -> "https://cdn.slimeread.com/"
            5 -> "https://black.slimeread.com/"
            else -> "https://objects.slimeread.com/"
        }

        baseUrl + path
    }
}
