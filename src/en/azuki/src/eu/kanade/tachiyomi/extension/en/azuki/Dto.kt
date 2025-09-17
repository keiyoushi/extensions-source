package eu.kanade.tachiyomi.extension.en.azuki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PageListDto(
    val data: PageDataDto,
)

@Serializable
class PageDataDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val image: ImageDto,
)

@Serializable
class ImageDto(
    val webp: List<ImageUrlDto>?,
    val jpg: List<ImageUrlDto>?,
)

@Serializable
class ImageUrlDto(
    val url: String,
    val width: Int,
)

@Serializable
class UserMangaStatusDto(
    @SerialName("purchased_chapter_uuids")
    val purchasedChapterUuids: List<String> = emptyList(),
    @SerialName("unlocked_chapter_uuids")
    val unlockedChapterUuids: List<String> = emptyList(),
)
