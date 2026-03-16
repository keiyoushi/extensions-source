package eu.kanade.tachiyomi.extension.en.akaicomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class MangaListResponse(
    @JsonNames("manga", "data", "series") val manga: List<MangaDto> = emptyList(),
    val ok: Boolean = false,
    val page: Int = 1,
    @JsonNames("pageSize", "page_size") val pageSize: Int = manga.size,
    val total: Int = manga.size,
)

@Serializable
data class MangaDto(
    @JsonNames("id", "lid", "series_id") val id: String = "",
    @JsonNames("series_name", "name", "title") val seriesName: String = "",
    @JsonNames("cover_url", "cover", "thumbnail", "image") val coverUrl: String? = null,
    @JsonNames("slug", "series_slug") val slug: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: String? = null,
    val status: String? = null,
    val type: String? = null,
    @JsonNames("alternative_name", "alt_name") val alternativeName: String? = null,
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
