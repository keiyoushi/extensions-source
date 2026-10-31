package eu.kanade.tachiyomi.extension.it.hentaiita

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
class PostDto(
    val slug: String,
    private val link: String,
    private val title: RenderedDto,
    @SerialName("date_gmt") val dateGmt: String? = null,
    // This site returns class_list as an object keyed by index, not as an array.
    @SerialName("class_list") private val classListJson: JsonElement? = null,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
    private val content: RenderedDto? = null,
) {
    val plainTitle: String get() = Parser.unescapeEntities(title.rendered, false)

    // The post text around the gallery: a synopsis on some posts.
    val contentText: String get() = content?.rendered?.let { Jsoup.parseBodyFragment(it).text() }.orEmpty()

    val classList: List<String>
        get() = when (val classes = classListJson) {
            is JsonArray -> classes.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonObject -> classes.values.mapNotNull { (it as? JsonPrimitive)?.content }
            else -> emptyList()
        }

    // Same form the previous versions stored, so library entries keep working.
    val path: String get() = link.toHttpUrlOrNull()?.encodedPath ?: "/$slug/"

    val terms: List<TermDto> get() = embedded?.terms.orEmpty().flatten()

    // media_details can be an empty array instead of an object, so it is read loosely.
    val thumbnail: String?
        get() {
            val media = embedded?.media?.firstOrNull() ?: return null
            val sizes = (media.details as? JsonObject)?.get("sizes") as? JsonObject
            val preferred = listOf("medium_large", "large", "medium").firstNotNullOfOrNull { size ->
                ((sizes?.get(size) as? JsonObject)?.get("source_url") as? JsonPrimitive)?.content
            }
            return preferred ?: media.sourceUrl
        }

    fun toSManga() = SManga.create().apply {
        url = path
        title = plainTitle
        thumbnail_url = thumbnail
    }
}

@Serializable
class RenderedDto(val rendered: String)

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val media: List<MediaDto> = emptyList(),
    @SerialName("wp:term") val terms: List<List<TermDto>> = emptyList(),
)

@Serializable
class MediaDto(
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("media_details") val details: JsonElement? = null,
)

@Serializable
class TermDto(
    val id: Int,
    val slug: String,
    private val name: String,
    val count: Int = 0,
) {
    val plainName: String get() = Parser.unescapeEntities(name, false)
}

@Serializable
class FilterDataDto(val categories: List<TermDto>)

@Serializable
class GalleryDto(val images: List<GalleryImageDto> = emptyList())

@Serializable
class GalleryImageDto(val url: String)
