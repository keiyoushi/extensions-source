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
    // Novels are text-only; blocked series need a login.
    val isReadable get() = type != "novel" && !isBlockedSeries

    // The site filters genres on the page of results it gets back, by English name.
    fun hasGenres(included: List<String>, excluded: List<String>): Boolean {
        val genres = metadata?.genres.orEmpty()
        return genres.containsAll(included) && genres.none { it in excluded }
    }

    private fun toSManga(siteHost: String) = SManga.create().apply {
        url = id.toString()
        title = this@ContentDto.title
        thumbnail_url = (coverImage ?: metadata?.coverImage ?: thumbnail)?.let { cover ->
            if (cover.startsWith("/")) {
                HttpUrl.Builder().scheme("https").host("$cdnPath.$siteHost").encodedPath(cover).build().toString()
            } else {
                cover
            }
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
            listOfNotNull(metadata?.originalTitle).plus(metadata?.altTitles.orEmpty()).distinct().takeIf { it.isNotEmpty() }?.let {
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
            "متوقف", "متوقف من الراو" -> SManga.ON_HIATUS
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
    val originalTitle: String? = null,
    val coverImage: String? = null,
)

@Serializable
class GenreDto(
    val en: String,
    val ar: String? = null,
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
        chapter_number = number.toFloatOrNull() ?: -1f
        scanlator = translator
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterRefDto(
    @SerialName("content_id") val contentId: Int,
)

@Serializable
class ReaderStateDto(
    val error: Boolean = false,
    // -1 until the first page is laid out.
    val laidOut: Int = -1,
    val origin: String? = null,
    val pages: List<ReaderPageDto>? = null,
)

@Serializable
class ReaderPageDto(
    val url: String? = null,
    val pieces: List<PieceDto>? = null,
)

// A stitched page: the chapter and index it was read at, to read it again once its pieces expire.
@Serializable
class PieceRefDto(
    val chapter: String,
    val index: Int,
    val origin: String,
    val pieces: List<PieceDto>,
)

// Position and size in fractions of the page.
@Serializable
class PieceDto(
    val url: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

object StringOrListSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonPrimitive(element.joinToString { (it as? JsonPrimitive)?.content ?: "" })
        else -> element
    }
}
