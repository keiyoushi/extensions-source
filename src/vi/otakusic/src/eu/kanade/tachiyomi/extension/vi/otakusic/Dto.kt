package eu.kanade.tachiyomi.extension.vi.otakusic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class ChaptersResponse(
    val data: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") val chapterName: JsonPrimitive,
    @SerialName("chapter_slug") val chapterSlug: String,
    @SerialName("chapter_original_slug") val chapterOriginalSlug: String,
    @SerialName("api_url") val apiUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("public_at") val publicAt: String? = null,
    val status: String? = null,
)

@Serializable
class ChapterPagesResponse(
    val data: ChapterPagesData,
)

@Serializable
class ChapterPagesData(
    @SerialName("domain_cdn") val domainCdn: String,
    val item: ChapterPagesItem,
)

@Serializable
class ChapterPagesItem(
    @SerialName("chapter_path") val chapterPath: String,
    @SerialName("chapter_image") val chapterImages: List<ChapterImage>,
)

@Serializable
class ChapterImage(
    @SerialName("image_page") val page: Int,
    @SerialName("image_file") val file: String,
)
