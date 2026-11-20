package eu.kanade.tachiyomi.extension.id.shinigami

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ShinigamiBrowseDto(
    val data: List<ShinigamiBrowseDataDto>,
    val meta: MetaDto,
)

@Serializable
class ShinigamiBrowseDataDto(
    @SerialName("cover_portrait_url") private val coverPortraitUrl: String?,
    @SerialName("cover_image_url") private val coverImageUrl: String?,
    @SerialName("manga_id") val mangaId: String,
    val title: String,
) {
    val thumbnail: String?
        get() = coverImageUrl?.takeIf { it.isNotBlank() }
            ?: coverPortraitUrl?.takeIf { it.isNotBlank() }
}

@Serializable
class MetaDto(
    @SerialName("has_next") val hasNext: Boolean,
    @SerialName("next_cursor") val nextCursor: String?,
)

@Serializable
class ShinigamiMangaDetailDto(
    val data: ShinigamiMangaDetailDataDto,
)

@Serializable
class ShinigamiMangaDetailDataDto(
    val title: String?,
    @SerialName("cover_portrait_url") private val coverPortraitUrl: String?,
    @SerialName("cover_image_url") private val coverImageUrl: String?,
    @SerialName("alternative_title") val altTitle: String?,
    val description: String = "",
    val status: Int = 0,
    val taxonomy: Map<String, List<TaxonomyItemDto>> = emptyMap(),
    @SerialName("weighted_score") val weightedScore: Double?,
    @SerialName("rating_count") val ratingCount: Int?,
    @SerialName("bookmark_count") val bookmarkCount: Long?,
    @SerialName("view_count") val viewCount: Long?,
) {
    val thumbnail: String?
        get() = coverImageUrl?.takeIf { it.isNotBlank() }
            ?: coverPortraitUrl?.takeIf { it.isNotBlank() }
}

@Serializable
class TaxonomyItemDto(
    val name: String,
)

@Serializable
class TaxonomyListDto(
    val data: List<TaxonomyTermDto>,
)

@Serializable
class TaxonomyTermDto(
    val name: String,
    val slug: String,
)

@Serializable
class FilterDataDto(
    val genres: List<TaxonomyTermDto> = emptyList(),
    val themes: List<TaxonomyTermDto> = emptyList(),
)

@Serializable
class ShinigamiChapterListDto(
    @SerialName("data") val chapterList: List<ShinigamiChapterListDataDto>,
    val meta: MetaDto,
)

@Serializable
class ShinigamiChapterListDataDto(
    @SerialName("release_date") val date: String = "",
    @SerialName("chapter_title") val title: String = "",
    @SerialName("chapter_number") val name: Double,
    @SerialName("chapter_id") val chapterId: String,
)

@Serializable
class ShinigamiPageListDto(
    @SerialName("data") val pageList: ShinigamiPagesDataDto,
)

@Serializable
class ShinigamiPagesDataDto(
    @SerialName("base_url") val baseUrl: String,
    @SerialName("chapter") val chapterPage: ShinigamiPagesData2Dto,
)

@Serializable
class ShinigamiPagesData2Dto(
    val path: String,
    @SerialName("data") val pages: List<String> = emptyList(),
)
