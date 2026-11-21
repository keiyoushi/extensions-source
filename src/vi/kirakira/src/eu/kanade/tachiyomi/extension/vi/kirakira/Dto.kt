package eu.kanade.tachiyomi.extension.vi.kirakira

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicListDto(
    val comics: List<ComicDto> = emptyList(),
    @SerialName("current_page") private val currentPage: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
) {
    fun toMangasPage(): MangasPage {
        val mangas = comics.mapNotNull { comic ->
            if (comic.type.equals("novel", ignoreCase = true)) return@mapNotNull null
            val slug = comic.id ?: return@mapNotNull null

            SManga.create().apply {
                title = comic.title
                url = "/comics/$slug"
                thumbnail_url = comic.thumbnail?.ifBlank { null } ?: comic.bannerImageUrl?.ifBlank { null }
            }
        }

        return MangasPage(mangas, currentPage < totalPages)
    }
}

@Serializable
class ComicDto(
    val id: String? = null,
    val title: String,
    val thumbnail: String? = null,
    @SerialName("banner_image_url") val bannerImageUrl: String? = null,
    val type: String? = null,
)

@Serializable
class ComicDetailsDto(
    val title: String,
    val thumbnail: String? = null,
    @SerialName("banner_image_url") val bannerImageUrl: String? = null,
    val description: String? = null,
    val authors: String? = null,
    val status: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class GenreListDto(
    val data: GenreListDataDto,
)

@Serializable
class GenreListDataDto(
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class GenreDto(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
class ChapterDto(
    val id: Long? = null,
    val name: String? = null,
    val coinPrice: Int? = null,
    val unlockAt: String? = null,
)

@Serializable
class ChapterPagesDto(
    val images: List<PageImageDto> = emptyList(),
    val coinPrice: Int? = null,
    val isPurchased: Boolean? = null,
)

@Serializable
class PageImageDto(
    val src: String? = null,
)

@Serializable
class ApiErrorDto(
    val message: String? = null,
)
