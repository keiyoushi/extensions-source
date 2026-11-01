package eu.kanade.tachiyomi.extension.vi.leesincomic

import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class ComicsResponse(
    val comics: List<CatalogComicDto> = emptyList(),
)

@Serializable
class ComicResponse(
    val comic: ComicDto,
)

@Serializable
class ChapterResponse(
    val chapter: ChapterImagesDto,
)

// get_comics embeds every chapter, so skips those subtrees.
@Serializable
class CatalogComicDto(
    val id: String,
    val slug: String,
    val title: String,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val summary: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("other_names") val otherNames: String? = null,
    val authors: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
    val views: Long = 0,
    val likes: Long = 0,
    val rating: Double = 0.0,
) {
    val altTitles: List<String>
        get() = parseAltTitles(otherNames)
}

@Serializable
class ComicDto(
    val id: String,
    val slug: String,
    val title: String,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val summary: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("other_names") val otherNames: String? = null,
    val authors: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
    val views: Long = 0,
    val likes: Long = 0,
    val rating: Double = 0.0,
    val chapters: List<ChapterDto> = emptyList(),
) {
    val altTitles: List<String>
        get() = parseAltTitles(otherNames)
}

private fun parseAltTitles(otherNames: String?): List<String> = otherNames?.takeIf { it.startsWith("[") }
    ?.runCatching { parseAs<List<String>>() }?.getOrNull()
    ?: emptyList()

@Serializable
class ChapterDto(
    val id: String,
    @JsonNames("chapter_number", "chapterNumber")
    val chapterNumber: Double? = null,
    val title: String? = null,
    @JsonNames("created_at", "createdAt")
    val createdAt: String? = null,
    @JsonNames("updated_at", "updatedAt")
    val updatedAt: String? = null,
)

@Serializable
class ChapterImagesDto(
    val images: List<String> = emptyList(),
)
