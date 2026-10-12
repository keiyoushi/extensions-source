package eu.kanade.tachiyomi.extension.es.reizucomics

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
class ComicRow(
    val id: String,
    val slug: String? = null,
    val title: String? = null,
    val description: String? = null,
    val cover_image_url: String? = null,
    val cover_r2_key: String? = null,
    val genres: List<String>? = null,
    val status: String? = null,
    val work_type: String? = null,
)

@Serializable
class RankingRow(
    @SerialName("comic_id") val comicId: String,
    val slug: String? = null,
    val title: String? = null,
    val cover_image_url: String? = null,
    val cover_r2_key: String? = null,
    val genres: List<String>? = null,
    val work_type: String? = null,
)

@Serializable
class RecentRow(
    val id: String,
    val slug: String? = null,
    val title: String? = null,
    val cover_image_url: String? = null,
    val cover_r2_key: String? = null,
    val genres: List<String>? = null,
    val status: String? = null,
    val work_type: String? = null,
    val total_count: Int? = null,
)

@Serializable
class ChapterRow(
    val id: String,
    val chapter_number: Double? = null,
    val title: String? = null,
    val is_locked: Boolean? = null,
    val is_special: Boolean? = null,
    val published_at: String? = null,
    val comics: ComicRow? = null,
)

@Serializable
class ChapterPages(
    @Serializable(with = ContentUrlSerializer::class)
    val content_url: List<String> = emptyList(),
    val content_r2_keys: List<String>? = null,
    val content_r2_keys_paged: List<String>? = null,
    val content_r2_keys_scroll: List<String>? = null,
    val is_locked: Boolean? = null,
)

// content_url is polymorphic: an object with paginated/scroll arrays,
// a plain array of URLs, a single URL string, or stringified JSON.
object ContentUrlSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        SerialDescriptor("ContentUrl", ListSerializer(String.serializer()).descriptor)

    override fun serialize(encoder: Encoder, value: List<String>) = ListSerializer(String.serializer()).serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<String> {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return emptyList()
        return when (element) {
            is JsonObject -> element.pageList()
            is JsonArray -> element.stringList()
            is JsonPrimitive -> {
                val text = element.contentOrNull.orEmpty()
                if (text.startsWith("{") || text.startsWith("[")) {
                    when (val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()) {
                        is JsonObject -> parsed.pageList()
                        is JsonArray -> parsed.stringList()
                        else -> emptyList()
                    }
                } else {
                    listOf(text).filter(String::isNotBlank)
                }
            }
            else -> emptyList()
        }
    }

    // The object holds paginated/scroll arrays. Scroll is the clean page list;
    // paginated prefixes it with a cover thumbnail the reader skips.
    private fun JsonObject.pageList(): List<String> {
        val scroll = this["scroll"].stringList()
        if (scroll.isNotEmpty()) return scroll
        return this["paginated"].stringList().drop(1)
    }

    private fun JsonElement?.stringList(): List<String> = (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}
