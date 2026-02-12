package eu.kanade.tachiyomi.extension.en.akaicomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListResponse(
    val manga: List<MangaDto>,
    val ok: Boolean,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class MangaDto(
    val id: String,
    @SerialName("series_name") val seriesName: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: String? = null,
    val status: String? = null,
    val type: String? = null,
    @SerialName("alternative_name") val alternativeName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("release_year") val releaseYear: String? = null,
)

@Serializable
data class ChapterListResponse(
    val chapters: List<ChapterDto>,
    val ok: Boolean,
    val total: Int,
    val totalChapters: Int,
)

@Serializable
data class ChapterDto(
    @SerialName("chapter_number") val chapterNumber: Int,
    @SerialName("created_at") val createdAt: String? = null,
    val id: Int,
    @SerialName("locked_by_coins") val lockedByCoins: Int = 0,
    @SerialName("manga_id") val mangaId: String,
)

@Serializable
data class PageListResponse(
    val ok: Boolean,
    val pages: List<String>,
    val total: Int,
)
