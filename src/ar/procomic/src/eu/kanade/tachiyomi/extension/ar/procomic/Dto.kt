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
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Serializable
class FeedDto(
    val data: List<ContentDto>,
    val meta: MetaDto,
) {
    val hasNextPage get() = meta.page * meta.limit < meta.total
}

@Serializable
class MetaDto(
    val page: Int,
    val limit: Int,
    val total: Int,
)

@Serializable
class ContentWrapperDto(
    val data: ContentDto,
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
    @SerialName("cdn_path") private val cdnPath: String? = null,
    private val rating: Double? = null,
    private val genres: List<GenreDto>? = null,
    private val metadata: MetadataDto? = null,
    private val isBlockedSeries: Boolean = false,
) {
    // Novels are text-only; blocked series need a login.
    val isReadable get() = type != "novel" && !isBlockedSeries

    private fun cover(apiHost: String): String? {
        // The reader's own cover host (app.procomic.im) is served to the app; metadata.coverImage's host 403s.
        val raw = thumbnail ?: metadata?.coverImage ?: return null
        return when {
            raw.startsWith("http") -> raw
            cdnPath != null -> "https://$cdnPath.$apiHost".toHttpUrl().newBuilder()
                .addPathSegments(raw.trimStart('/'))
                .build()
                .toString()
            else -> null
        }
    }

    fun toSManga(apiHost: String) = SManga.create().apply {
        url = id.toString()
        title = this@ContentDto.title
        thumbnail_url = cover(apiHost)
        memo = buildJsonObject {
            put("slug", slug)
        }
    }

    fun toDetails(apiHost: String) = toSManga(apiHost).apply {
        author = metadata?.author
        artist = metadata?.artist
        description = buildString {
            this@ContentDto.description?.let { append(it.trim(), "\n\n") }
            rating?.let { append("★ ", it, "\n") }
            val altTitles = listOfNotNull(metadata?.originalTitle) + metadata?.altTitles.orEmpty()
            altTitles.takeIf { it.isNotEmpty() }?.let { titles ->
                append("عناوين بديلة\n")
                titles.forEach { append("- ", it, "\n") }
            }
        }.trim().takeIf { it.isNotEmpty() }
        genre = buildList {
            add(type.replaceFirstChar(Char::titlecase))
            metadata?.origin?.let { add(it.replaceFirstChar(Char::titlecase)) }
            metadata?.year?.let { add(it) }
            // The Arabic genre names from the top-level list; metadata.genres is English only.
            genres?.let { addAll(it.map(GenreDto::displayName)) } ?: metadata?.genres?.let { addAll(it) }
            metadata?.tags?.let { addAll(it) }
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
    val originalTitle: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val altTitles: List<String>? = null,
    val coverImage: String? = null,
)

@Serializable
class ChapterListDto(
    val data: List<ChapterDto>,
    val meta: MetaDto,
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
class ReaderWrapperDto(
    val data: ReaderDto,
)

@Serializable
class ReaderDto(
    val images: List<String> = emptyList(),
    val maps: List<MapEntryDto> = emptyList(),
    val readerSession: SessionDto? = null,
    val deferredMedia: SessionDto? = null,
)

@Serializable
class SessionDto(
    val token: String,
)

@Serializable
class MapEntryDto(
    val dim: List<Int>,
    val mode: String,
    val pieces: List<String>,
    val order: List<Int>,
)

@Serializable
class GenresWrapperDto(
    val data: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: Int,
    val ar: String = "",
    val en: String = "",
) {
    val displayName get() = ar.ifBlank { en }
}

@Serializable
class RegisterRequestDto(
    val publicKeyDer: String,
)

@Serializable
class RegisterResponseDto(
    val deviceId: String,
)

object StringOrListSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonPrimitive(element.joinToString { (it as? JsonPrimitive)?.content ?: "" })
        else -> element
    }
}
