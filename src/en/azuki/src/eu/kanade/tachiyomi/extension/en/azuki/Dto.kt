package eu.kanade.tachiyomi.extension.en.azuki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PageListDto {
    val data: PageDataDto? = null
}

@Serializable
class PageDataDto {
    val pages: List<PageDto> = emptyList()
}

@Serializable
class PageDto {
    val image: ImageDto? = null
}

@Serializable
class ImageDto {
    val webp: List<ImageUrlDto>? = null
    val jpg: List<ImageUrlDto>? = null
}

@Serializable
class ImageUrlDto {
    val url: String = ""
    val width: Int = 0
}

@Serializable
class UserMangaStatusDto {
    @SerialName("purchased_chapter_uuids")
    val purchasedChapterUuids: List<String> = emptyList()

    @SerialName("unlocked_chapter_uuids")
    val unlockedChapterUuids: List<String> = emptyList()
}
