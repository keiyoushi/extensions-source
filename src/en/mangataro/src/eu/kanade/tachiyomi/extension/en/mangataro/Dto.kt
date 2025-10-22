package eu.kanade.tachiyomi.extension.en.mangataro

import keiyoushi.utils.toJsonString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
@Suppress("unused")
class SearchPayload(
    private val page: Int,
    private val search: String,
    @Serializable(with = StringifiedListSerializer::class)
    private val years: List<Int>,
    @Serializable(with = StringifiedListSerializer::class)
    private val genres: List<Int>,
    @Serializable(with = StringifiedListSerializer::class)
    private val types: List<String>,
    @Serializable(with = StringifiedListSerializer::class)
    private val statuses: List<String>,
    private val sort: String,
    private val genreMatchMode: String,
)

class StringifiedListSerializer<T>(elementSerializer: KSerializer<T>) :
    JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {

    override fun transformSerialize(element: JsonElement) =
        JsonPrimitive(element.toJsonString())
}

@Serializable
@Suppress("unused")
class SearchQueryPayload(
    val limit: Int,
    val query: String,
)

@Serializable
class SearchQueryResponse(
    val results: List<Manga>,
) {
    @Serializable
    class Manga(
        val id: Int,
        val slug: String,
        val title: String,
        val thumbnail: String,
        val type: String,
        val description: String,
        val status: String,
    )
}

@Serializable
class BrowseManga(
    val id: String,
    val url: String,
    val title: String,
    val cover: String,
    val type: String,
    val description: String,
    val status: String,
)

@Serializable
class MangaUrl(
    val id: String,
    val slug: String,
)

@Serializable
class MangaDetails(
    val id: Int,
    val slug: String,
    val title: Rendered,
    val content: Rendered,
    @SerialName("featured_media")
    val featuredMedia: Int,
    @SerialName("class_list")
    private val classList: List<String>,
) {
    fun getFromClassList(type: String): List<String> {
        return classList.filter { it.startsWith("$type-") }
            .map {
                it.substringAfter("$type-")
                    .split("-")
                    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
            }
    }
}

@Serializable
class Thumbnail(
    @SerialName("source_url")
    val url: String,
)

@Serializable
class Rendered(
    val rendered: String,
)
