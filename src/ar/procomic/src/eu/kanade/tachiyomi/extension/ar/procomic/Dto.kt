package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class SearchResponse(
    val data: List<SearchItem>,
    val meta: PaginationMeta? = null,
)

@Serializable
data class SearchItem(
    val id: Int,
    val title: String,
    val slug: String,
    val type: String,
    val progress: String? = null,
    val description: String? = null,
    val status: String? = null,
    val thumbnail: String? = null,
    val slider_image: String? = null,
    val slider_mobile_image: String? = null,
    val is_sensitive_image: Boolean? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val google_drive_folder_id: String? = null,
    val metadata: SearchMetadata? = null,
    val coverImage: String? = null,
    val coverImageApp: CoverImageApp? = null,
    @Serializable(with = MatchScoreSerializer::class)
    val match_score: Double? = null,
)

@Serializable
data class SearchMetadata(
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
    @SerialName("cover_image_app") val coverImageApp: CoverImageApp? = null,
)

@Serializable
data class CoverImageApp(
    val mobile: String? = null,
    val desktop: String? = null,
    val card: CardImage? = null,
)

@Serializable
data class CardImage(
    val mobile: String? = null,
    val desktop: String? = null,
)

@Serializable
data class PaginationMeta(
    @SerialName("pages") val totalPages: Int? = null,
    @SerialName("page") val currentPage: Int? = null,
) {
    fun hasNextPage() = totalPages != null && currentPage != null && totalPages > currentPage
}

@Serializable
data class ApiManga(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    val type: String,
    val progress: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val metadata: ApiMetadata? = null,
    val coverImageApp: CoverImageApp? = null,
    val chapters: List<ApiChapter>? = null,
    @SerialName("chaptersCount") val chaptersCount: Int? = null,
)

@Serializable
data class ApiMetadata(
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
    @SerialName("cover_image_app") val coverImageApp: CoverImageApp? = null,
    val descriptions: Map<String, String>? = null,
)

@Serializable
data class ApiChapter(
    val id: Int,
    @SerialName("chapter_number") val chapterNumber: String,
    val title: String? = null,
    val language: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("coins_required") val coins: Int? = null,
    @SerialName("uploader_nickname") val uploader: String? = null,
    val status: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val metadata: ChapterMetadata? = null,
)

@Serializable
data class ChapterMetadata(
    @SerialName("teamId") val teamId: Int? = null,
    val group: String? = null,
    val groupName: String? = null,
    val scanlationGroup: String? = null,
    val scanlationGroups: String? = null,
    val team: String? = null,
    val teamName: String? = null,
    val team_name: String? = null,
    val translator: String? = null,
    val translatorName: String? = null,
    val translatorTeam: String? = null,
    val translatorTeamName: String? = null,
    val uploaderTeam: String? = null,
    val uploaderTeamName: String? = null,
    val uploader_team: String? = null,
)

@Serializable
data class ChapterImages(
    @SerialName("appImages") val appImages: List<AppImage>,
    @SerialName("pieceRenderMode") val pieceRenderMode: String? = null,
)

@Serializable
data class AppImage(
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

object MatchScoreSerializer : JsonTransformingSerializer<Double>(Double.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when {
        element is JsonPrimitive -> element
        else -> JsonPrimitive(0.0)
    }
}
