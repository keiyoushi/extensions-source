package eu.kanade.tachiyomi.extension.th.manga168

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// --- /api/manga/mangas?page=N and /api/manga/daily-popular ---

@Serializable
class MangaListDto(
    val page: String = "1",
    val pagecount: String = "1",
    val data: List<ApiMangaDto> = emptyList(),
)

@Serializable
class PopularDto(
    val data: List<ApiMangaDto> = emptyList(),
)

@Serializable
class ApiMangaDto(
    val id: String,
    val slug: String,
    val title: String,
    val coverImage: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@ApiMangaDto.title
        thumbnail_url = coverImage?.ifEmpty { null }
    }
}

@Serializable
class ImagesDto(
    val data: List<String> = emptyList(),
)

// --- Next.js page data (/manga catalog for search, /manga/$slug details) ---

@Serializable
class SeriesPageDto(
    val series: List<SeriesDto> = emptyList(),
)

@Serializable
class SeriesDto(
    val id: String,
    val slug: String,
    val title: String,
    val coverImage: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val updatedAt: String? = null,
    val views: JsonElement? = null,
    val chapters: List<SeriesChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@SeriesDto.title
        thumbnail_url = coverImage?.ifEmpty { null }
    }
}

@Serializable
class SeriesChapterDto(
    val id: String,
    val title: String? = null,
    val number: Double? = null,
    val updatedAt: String? = null,
)
