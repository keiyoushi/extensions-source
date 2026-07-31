package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
    val cdnPath: String? = null,
    @Serializable(DeferredMediaSerializer::class)
    val deferredMedia: DeferredMedia? = null,
)

@Serializable
class AppImage(
    val mobile: String? = null,
    val desktop: String? = null,
)

@Serializable
class DeferredMedia(
    val token: String,
)
object DeferredMediaSerializer : JsonTransformingSerializer<DeferredMedia?>(DeferredMedia.serializer().nullable) {
    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is JsonPrimitive) JsonNull else element
}

@Serializable
class DeferredResponse(
    val data: DeferredData,
)

@Serializable
class DeferredData(
    val images: List<String> = emptyList(),
    val maps: List<MapEntry> = emptyList(),
)

@Serializable
class MapEntry(
    val token: String,
    val method: String? = null,
)

@Serializable
class ScrambledImage(
    val order: List<Int>,
    val pieces: List<String>,
    val dim: List<Int>,
    val rects: List<MapRect>,
)

@Serializable
class MapRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

@Serializable
class ProxyPlanResponse(
    val data: ProxyPlanData,
)

@Serializable
class ProxyPlanData(
    val map: ScrambledImage,
)

object StringOrListSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonPrimitive -> element
        is JsonArray -> JsonPrimitive(element.joinToString("\n") { (it as? JsonPrimitive)?.content ?: it.toString() })
        else -> element
    }
}
