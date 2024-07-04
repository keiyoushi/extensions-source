package eu.kanade.tachiyomi.extension.ja.nicovideoseiga

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
class ApiResponse<T>(
    val data: Data<T>,
)

@Serializable
data class Data<T>(
    @Serializable(with = SingleResultSerializer::class)
    val result: List<T>,
    val extra: Extra? = null,
)

@Serializable
data class Extra(
    @SerialName("has_next")
    val hasNext: Boolean? = null,
)

class SingleResultSerializer<T>(serializer: KSerializer<T>) : JsonTransformingSerializer<List<T>>(ListSerializer(serializer)) {
    // Wrap single results in a list. Leave multiple results as is.
    override fun transformSerialize(element: JsonElement): JsonElement {
        return when (element) {
            is JsonArray -> element
            is JsonObject -> JsonArray(listOf(element))
            else -> throw IllegalStateException("Unexpected JSON element type: $element")
        }
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonArray) element else JsonArray(listOf(element))
    }
}

// Result objects

@Serializable
data class PopularManga(
    val id: Int,
    val title: String,
    val author: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String,
)

@Serializable
data class Manga(
    val id: Int,
    val meta: MangaMetadata,
) {
    @Serializable
    data class MangaMetadata(
        val title: String,
        @SerialName("display_author_name")
        val author: String,
        val description: String,
        @SerialName("serial_status")
        val serialStatus: String,
        @SerialName("square_image_url")
        val thumbnailUrl: String,
        @SerialName("share_url")
        val shareUrl: String,
    )
}

// Frames are the internal name for pages in the API
@Serializable
data class Frame(
    val id: Int,
    val meta: FrameMetadata,
) {
    @Serializable
    data class FrameMetadata(
        @SerialName("source_url")
        val sourceUrl: String,
    )
}

// Chapters are known as Episodes internally in the API
@Serializable
data class Chapter(
    val id: Int,
    val meta: ChapterMetadata,
    @SerialName("own_status")
    val ownership: Ownership,
) {
    @Serializable
    data class ChapterMetadata(
        val title: String,
        val number: Int,
        @SerialName("created_at")
        val createdAt: Long,
    )

    @Serializable
    data class Ownership(
        @SerialName("sell_status")
        val sellStatus: String,
    )
}
