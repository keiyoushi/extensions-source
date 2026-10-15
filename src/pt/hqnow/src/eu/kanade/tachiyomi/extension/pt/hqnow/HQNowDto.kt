package eu.kanade.tachiyomi.extension.pt.hqnow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class HqsByFiltersDto(val getHqsByFilters: List<HqNowComicBookDto>)

@Serializable
class RecentlyUpdatedHqsDto(val getRecentlyUpdatedHqs: List<HqNowComicBookDto>)

@Serializable
class HqsByNameDto(val getHqsByName: List<HqNowComicBookDto>)

@Serializable
class HqsByIdDto(val getHqsById: List<HqNowComicBookDto>)

@Serializable
class ChapterByIdDto(val getChapterById: HqNowChapterDto)

@Serializable
class HqNowComicBookDto(
    @SerialName("capitulos") val chapters: List<HqNowChapterDto> = emptyList(),
    @SerialName("hqCover") val cover: String? = "",
    val id: Int,
    val name: String,
    val publisherName: String? = "",
    val status: String? = "",
    val synopsis: String? = "",
)

@Serializable
class HqNowChapterDto(
    val id: Int = 0,
    val name: String,
    val number: String,
    val pictures: List<HqNowPageDto> = emptyList(),
)

@Serializable
class HqNowPageDto(
    val pictureUrl: String,
)
