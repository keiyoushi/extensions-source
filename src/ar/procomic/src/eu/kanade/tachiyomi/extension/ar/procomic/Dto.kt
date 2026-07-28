package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
class SearchResponse(
    val data: List<SearchItem>,
    val meta: PaginationMeta? = null,
)

@Serializable
class SearchItem(
    val id: Int,
    val title: String,
    val slug: String,
    val type: String,
    val coverImage: String? = null,
    val coverImageApp: CoverImageApp? = null,
)

@Serializable
class CoverImageApp(
    val mobile: String? = null,
    val desktop: String? = null,
)

@Serializable
class PaginationMeta(
    @SerialName("pages") val totalPages: Int? = null,
    @SerialName("page") val currentPage: Int? = null,
)

@Serializable
class ApiManga(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    val type: String,
    val progress: String? = null,
    val metadata: ApiMetadata? = null,
    val coverImageApp: CoverImageApp? = null,
    val chapters: List<ApiChapter>? = null,
)

@Serializable
class ApiMetadata(
    val originalTitle: String? = null,
    val altTitles: List<String>? = null,
    @Serializable(with = StringOrListSerializer::class)
    val author: String? = null,
    @Serializable(with = StringOrListSerializer::class)
    val artist: String? = null,
    val year: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val origin: String? = null,
    val coverImage: String? = null,
)

@Serializable
class ApiChapter(
    val id: Int,
    @SerialName("chapter_number") val chapterNumber: String,
    val title: String? = null,
    val language: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("coins_required") val coins: Int? = null,
    @SerialName("uploader_nickname") val uploader: String? = null,
)

@Serializable
class ChapterImages(
    @SerialName("appImages") val appImages: List<AppImage>,
)

@Serializable
class AppImage(
    val mobile: String? = null,
    val desktop: String? = null,
)

object StringOrListSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when {
        element is JsonPrimitive -> element
        element is JsonArray -> JsonPrimitive(element.map { (it as? JsonPrimitive)?.content ?: it.toString() }.joinToString("\n"))
        else -> element
    }
}
