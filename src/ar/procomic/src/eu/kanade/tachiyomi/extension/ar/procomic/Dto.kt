package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class ContentPageDto(
    val data: List<ContentDto>,
    val meta: MetaDto,
) {
    val hasNextPage get() = meta.page < meta.pages
}

@Serializable
class MetaDto(
    val page: Int,
    val pages: Int,
)

@Serializable
class ContentDto(
    private val id: Int,
    private val title: String,
    private val slug: String,
    private val type: String,
    private val description: String? = null,
    private val progress: String? = null,
    private val thumbnail: String? = null,
    private val coverImage: String? = null,
    @SerialName("cdn_path") private val cdnPath: String,
    private val metadata: MetadataDto? = null,
    private val isBlockedSeries: Boolean = false,
) {
    // Novels are text-only and blocked series need a login.
    val isReadable get() = type != "novel" && !isBlockedSeries

    fun toSManga(siteHost: String) = SManga.create().apply {
        url = id.toString()
        title = this@ContentDto.title
        thumbnail_url = when {
            thumbnail == null -> coverImage
            thumbnail.startsWith("/") -> HttpUrl.Builder()
                .scheme("https")
                .host("$cdnPath.$siteHost")
                .encodedPath(thumbnail)
                .build()
                .toString()
            else -> thumbnail
        }
        memo = buildJsonObject {
            put("slug", slug)
            put("type", type)
        }
    }

    fun toDetails(siteHost: String) = toSManga(siteHost).apply {
        author = metadata?.author
        artist = metadata?.artist
        description = buildString {
            this@ContentDto.description?.let { append(it.trim(), "\n\n") }
            metadata?.altTitles?.takeIf { it.isNotEmpty() }?.let {
                append("عناوين بديلة\n")
                it.forEach { append("- ", it, "\n") }
            }
        }.trim().takeIf { it.isNotEmpty() }
        genre = buildList {
            add(type.replaceFirstChar(Char::titlecase))
            metadata?.let { meta ->
                meta.origin?.let { add(it.replaceFirstChar(Char::titlecase)) }
                meta.year?.let { add(it) }
                meta.genres?.let { addAll(it) }
                meta.tags?.let { addAll(it) }
            }
        }.joinToString()
        status = when (progress?.trim()) {
            "مستمر" -> SManga.ONGOING
            "مكتمل" -> SManga.COMPLETED
            "متوقف" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class MetadataDto(
    @Serializable(with = StringOrListSerializer::class) val author: String? = null,
    @Serializable(with = StringOrListSerializer::class) val artist: String? = null,
    val origin: String? = null,
    val year: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val altTitles: List<String>? = null,
)

@Serializable
class ChapterPageDto(
    val data: List<ChapterDto>,
    val total: Int,
)

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_number") private val number: String,
    private val title: String? = null,
    private val language: String,
    private val translator: String? = null,
    @SerialName("published_at") private val publishedAt: String? = null,
    private val lockedForever: Boolean = false,
    private val lockedByCoins: Boolean = false,
    private val lockedByShortlink: Boolean = false,
    private val lockedByExclusive: Boolean = false,
) {
    val isArabic get() = language == "AR"
    val isLocked get() = lockedForever || lockedByCoins || lockedByShortlink || lockedByExclusive

    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = id.toString()
        memo = buildJsonObject {
            put("slug", seriesSlug)
            put("number", number)
        }
        name = buildString {
            append("\u200F")
            if (isLocked) append("🔒 ")
            append("الفصل ", number)
            title?.takeIf { it.isNotBlank() && it != number }?.let { append(" \u200F- ", it) }
        }
        scanlator = translator
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterRefDto(
    @SerialName("content_id") val contentId: Int,
)

@Serializable
class ReaderDto(
    val chapterId: Int,
    val appImages: List<AppImageDto>,
    val cdnPath: String,
    val deferredMedia: DeferredMediaDto? = null,
)

@Serializable
class AppImageDto(
    val mobile: String? = null,
    val desktop: String? = null,
)

@Serializable
class DeferredMediaDto(
    val token: String,
)

@Serializable
class DeferredPageDto(
    val data: DeferredDataDto,
)

@Serializable
class DeferredDataDto(
    val images: List<String>,
    val maps: List<MapEntryDto>,
)

@Serializable
class MapEntryDto(
    val token: String,
    val method: String,
)

@Serializable
class MapPlanResponseDto(
    val data: MapPlanWrapperDto,
)

@Serializable
class MapPlanWrapperDto(
    val map: MapPlanDto,
)

@Serializable
class MapPlanDto(
    val dim: List<Int>,
    val pieces: List<String>,
    val order: List<Int>,
    val rects: List<MapRectDto>,
)

@Serializable
class MapRectDto(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

@Serializable
class MovedDto(
    val targetUrl: String,
)

@Serializable
class SignRequestDto(
    val url: String,
)

@Serializable
class SignedImageDto(
    val token: String,
    val expires: Long,
)

@Serializable
class MapRequestDto(
    val chapterId: Int,
    val plan: MapPlanBodyDto,
)

@Serializable
class MapPlanBodyDto(
    val token: String,
    val method: String,
    val cdnPath: String,
    val pageIndex: Int,
)

object StringOrListSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonPrimitive(element.joinToString { (it as? JsonPrimitive)?.content ?: "" })
        else -> element
    }
}
