package eu.kanade.tachiyomi.extension.ru.astramanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(val data: SearchData)

@Serializable
class SearchData(
    val titles: List<TitleDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("current_page") val currentPage: Int = 1,
)

@Serializable
class TitleDetailResponse(val data: TitleDto)

@Serializable
class TitleDto(
    val id: Int,
    val slug: String,
    val name: String,
    @SerialName("secondary_name") val secondaryName: String? = null,
    @SerialName("alternative_names") val alternativeNames: List<String>? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("cover_versions") val coverVersions: CoverVersions? = null,
    val description: String? = null,
    val type: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val genres: List<Named>? = null,
    val tags: List<Named>? = null,
    val publishers: List<Named>? = null,
    @SerialName("publishing_house") val publishingHouse: Named? = null,
)

@Serializable
class CoverVersions(
    val high: String? = null,
    val mid: String? = null,
)

@Serializable
class Named(val name: String? = null)

@Serializable
class BranchesResponse(val data: BranchesData)

@Serializable
class BranchesData(val branches: List<BranchDto> = emptyList())

@Serializable
class BranchDto(
    val id: Int,
    @SerialName("is_main") val isMain: Boolean? = null,
    @SerialName("count_chapters") val countChapters: Int? = null,
)

@Serializable
class ChaptersResponse(val data: ChaptersData)

@Serializable
class ChaptersData(
    val items: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Long,
    val number: Float = 0f,
    @SerialName("volume_number") val volumeNumber: Int? = null,
    val name: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
class PagesResponse(val data: PagesData)

@Serializable
class PagesData(val pages: List<PageDto> = emptyList())

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)
