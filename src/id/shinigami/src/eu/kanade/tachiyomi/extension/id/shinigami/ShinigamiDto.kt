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
    @SerialName("cover_image_url") val thumbnail: String? = "",
    @SerialName("manga_id") val mangaId: String? = "",
    val title: String? = "",
)

@Serializable
class MetaDto(
    val page: Int,
    @SerialName("total_page") val totalPage: Int,
)

@Serializable
class ShinigamiMangaDetailDto(
    val data: ShinigamiMangaDetailDataDto,
)

@Serializable
class ShinigamiMangaDetailDataDto(
    val description: String = "",
    val status: Int = 0,
    val taxonomy: Map<String, List<TaxonomyItemDto>> = emptyMap(),
)

@Serializable
class TaxonomyItemDto(
    val name: String,
)

@Serializable
class ShinigamiChapterListDto(
    @SerialName("data") val chapterList: List<ShinigamiChapterListDataDto>,
)

@Serializable
class ShinigamiChapterListDataDto(
    @SerialName("release_date") val date: String = "",
    @SerialName("chapter_title") val title: String = "",
    @SerialName("chapter_number") val name: Double = 0.0,
    @SerialName("chapter_id") val chapterId: String = "",
)

@Serializable
class ShinigamiPageListDto(
    @SerialName("data") val pageList: ShinigamiPagesDataDto,
)

@Serializable
class ShinigamiPagesDataDto(
    @SerialName("chapter") val chapterPage: ShinigamiPagesData2Dto,
)

@Serializable
class ShinigamiPagesData2Dto(
    val path: String,
    @SerialName("data") val pages: List<String> = emptyList(),
)
