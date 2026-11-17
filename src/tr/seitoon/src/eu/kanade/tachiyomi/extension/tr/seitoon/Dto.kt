package eu.kanade.tachiyomi.extension.tr.seitoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.escapeMarkdown
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class SeriesListResponse(val data: SeriesListData, val meta: CursorMeta)

@Serializable
class SeriesListData(val series: List<SeriesDto>)

@Serializable
class SeriesResponse(val data: SeriesData)

@Serializable
class SeriesData(val series: SeriesDto)

@Serializable
class CursorMeta(
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class SeriesDto(
    private val slug: String,
    private val title: String,
    @SerialName("alternative_titles") private val alternativeTitles: List<String> = emptyList(),
    @SerialName("cover_image") private val coverImage: ImageDto? = null,
    @SerialName("content_type") val contentType: String? = null,
    private val creators: List<CreatorDto> = emptyList(),
    private val description: String? = null,
    @SerialName("editorial_note") private val editorialNote: String? = null,
    private val genres: List<TermDto> = emptyList(),
    private val tags: List<TermDto> = emptyList(),
    @SerialName("publication_status") private val publicationStatus: String? = null,
    private val signals: SignalsDto? = null,
) {
    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/comics/$slug"
        title = this@SeriesDto.title.trim()
        thumbnail_url = coverImage?.objectKey?.let { mediaUrl(baseUrl, it) }
    }

    fun toSMangaDetails(baseUrl: HttpUrl) = toSManga(baseUrl).apply {
        author = creators.names("author")
        artist = creators.names("artist")
        genre = (listOfNotNull(TYPES[contentType]) + genres.map { it.name } + tags.map { it.name })
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when (publicationStatus) {
            "current", "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            // "unknown" is shown as "Sezon Finali" on the site
            "hiatus", "unknown" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(signals?.ratingAverage?.takeIf { signals.ratingCount > 0 }, max = 5, votes = signals?.ratingCount)
            .field("Publisher", creators.names("publisher"))
            .field("Studio", creators.names("studio"))
            .field("Translator", creators.names("translator"))
            .field("Editor", creators.names("editor"))
            .description(this@SeriesDto.description)
            .markdown(editorialNote?.trim()?.takeIf { it.isNotEmpty() }?.let { "**Editor's note:** ${it.escapeMarkdown()}" })
            .altTitles(alternativeTitles.filterNot { it.equals(title, ignoreCase = true) })
            .build()
        if (publicationStatus == "completed") update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun List<CreatorDto>.names(role: String) = filter { it.role == role }
        .map { it.displayName.trim() }
        .distinct()
        .joinToString()
        .ifEmpty { null }
}

@Serializable
class ImageDto(@SerialName("object_key") val objectKey: String? = null)

@Serializable
class CreatorDto(
    @SerialName("display_name") val displayName: String,
    val role: String,
)

@Serializable
class TermDto(val name: String)

@Serializable
class SignalsDto(
    @SerialName("rating_average") val ratingAverage: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
)

@Serializable
class ChapterListResponse(val data: ChapterListData, val meta: CursorMeta)

@Serializable
class ChapterListData(val chapters: List<ChapterDto>)

@Serializable
class ChapterDto(
    val id: String,
    val slug: String,
    @SerialName("canonical_path") private val canonicalPath: String,
    @SerialName("chapter_number") private val chapterNumber: String,
    private val title: String? = null,
    @SerialName("access_level") private val accessLevel: String,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        val locked = accessLevel != "free"
        val number = chapterNumber.toFloat()
        url = canonicalPath
        name = buildString {
            if (locked) append("🔒 ")
            append("Bölüm ${number.toString().removeSuffix(".0")}")
            this@ChapterDto.title?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" - $it") }
        }
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
        memo = buildJsonObject {
            put("id", id)
            if (locked) put("locked", true)
        }
    }
}

@Serializable
class ManifestResponse(val data: ManifestData)

@Serializable
class ManifestData(val manifest: ManifestDto)

@Serializable
class ManifestDto(private val assets: List<AssetDto>) {
    // The pages the reader shows are the assets with image variants
    val pages: List<VariantDto>
        get() = assets.filter { it.variant != null }.sortedBy { it.pageNumber }.map { it.variant!! }
}

@Serializable
class AssetDto(
    @SerialName("page_number") val pageNumber: Int = 0,
    private val variants: Map<String, VariantDto>? = null,
) {
    val variant: VariantDto?
        get() = variants?.get("desktop") ?: variants?.values?.firstOrNull()
}

@Serializable
class VariantDto(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("delivery_token") val deliveryToken: String,
)

@Serializable
class TokenDto(val exp: Long)

@Serializable
class FilterDto(
    val name: String,
    val label: String,
    val options: List<OptionDto>,
)

@Serializable
class OptionDto(val label: String, val value: String)

// The site's media links are keys under its media host, or links of their own
private fun mediaUrl(baseUrl: HttpUrl, key: String): String = when {
    key.startsWith("http://") || key.startsWith("https://") -> key
    key.startsWith("/") -> baseUrl.resolve(key)!!.toString()
    else -> "$MEDIA_URL/$key"
}

private const val MEDIA_URL = "https://media.seitoon.com.tr/seitoon-media"

private val TYPES = mapOf(
    "manga" to "Manga",
    "manhwa" to "Manhwa",
    "manhua" to "Manhua",
    "comic" to "Çizgi Roman",
    "webtoon" to "Webtoon",
)
