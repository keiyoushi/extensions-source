package eu.kanade.tachiyomi.extension.id.shinigami

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShinigamiBrowseDto(
    val data: List<ShinigamiBrowseDataDto>,
    val meta: MetaDto,
)

@Serializable
data class ShinigamiBrowseDataDto(
    @SerialName("cover_image_url") val thumbnail: String? = "",
    @SerialName("manga_id") val mangaId: String? = "",
    val title: String? = "",
)

@Serializable
data class MetaDto(
    val page: Int,
    @SerialName("total_page") val totalPage: Int,
)

@Serializable
data class ShinigamiMangaDetailDto(
    val data: ShinigamiMangaDetailDataDto,
)

@Serializable
data class ShinigamiMangaDetailDataDto(
    val description: String = "",
//    @SerialName("alternative_title") val alternativeTitle: String = "",
    val status: Int = 0,
    val taxonomy: Map<String, List<TaxonomyItemDto>> = emptyMap(),
)

@Serializable
data class TaxonomyItemDto(
    val name: String,
)

@Serializable
data class ShinigamiChapterListDto(
    @SerialName("data") val chapterList: List<ShinigamiChapterListDataDto>,
    val meta: MetaDto,
)

@Serializable
data class ShinigamiChapterListDataDto(
    @SerialName("release_date") val date: String = "",
    @SerialName("chapter_title") val title: String = "",
    @SerialName("chapter_number") val name: Int = 0,
    @SerialName("chapter_id") val chapterId: String = "",
)

@Serializable
data class ShinigamiPageListDto(
    @SerialName("data") val pageList: ShinigamiPagesDataDto,
)

@Serializable
data class ShinigamiPagesDataDto(
    @SerialName("chapter") val chapterPage: ShinigamiPagesData2Dto,
)

@Serializable
data class ShinigamiPagesData2Dto(
    val path: String,
    @SerialName("data") val pages: List<String> = emptyList(),
)
