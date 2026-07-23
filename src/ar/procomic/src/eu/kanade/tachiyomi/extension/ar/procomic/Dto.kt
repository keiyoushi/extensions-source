package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiManga(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    val type: String,
    val progress: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val metadata: ApiMetadata? = null,
    val coverImageApp: CoverImageApp? = null,
    val chapters: List<ApiChapter>? = null,
    @SerialName("chaptersCount") val chaptersCount: Int? = null,
)

@Serializable
data class ApiMetadata(
    val originalTitle: String? = null,
    val altTitles: List<String>? = null,
    val author: String? = null,
    val artist: String? = null,
    val year: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val origin: String? = null,
    val coverImage: String? = null,
)

@Serializable
data class ApiChapter(
    val id: Int,
    @SerialName("chapter_number") val chapterNumber: String,
    val title: String? = null,
    val language: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("coins_required") val coins: Int? = null,
    @SerialName("uploader_nickname") val uploader: String? = null,
)

@Serializable
data class CoverImageApp(
    val desktop: String? = null,
    val card: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ChapterImages(
    @SerialName("appImages") val appImages: List<AppImage>,
    @SerialName("pieceRenderMode") val pieceRenderMode: String? = null,
)

@Serializable
data class SearchResponse(
    val data: List<SearchItem>,
    val meta: PaginationMeta? = null,
)

@Serializable
data class SearchItem(
    val id: Int,
    val title: String,
    val slug: String,
    val type: String,
    val progress: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val coverImage: String? = null,
    val coverImageApp: CoverImageApp? = null,
)

@Serializable
data class PaginationMeta(
    private val pages: Int? = null,
    private val page: Int? = null,
) {
    fun hasNextPage() = pages != null && page != null && pages > page
}

@Serializable
data class AppImage(
    val mobile: String? = null,
    val desktop: String? = null,
)
