package eu.kanade.tachiyomi.multisrc.kemono

import eu.kanade.tachiyomi.multisrc.kemono.KemonoCreatorDto.Companion.serviceName
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import java.time.Instant as JavaInstant

@Serializable
class KemonoFavoritesDto(
    val id: String,
    val service: String,
    @SerialName("faved_seq") val favedSeq: Long,
)

@Serializable
class KemonoCreatorDto(
    val id: String,
    val name: String,
    val service: String,
    @SerialName("public_id") val publicId: String? = null,
    private val indexed: JsonPrimitive? = null,
    private val updated: JsonPrimitive? = null,
    val favorited: Int = -1,
) {
    val indexedDate get() = indexed.toEpochMillis()
    val updatedDate get() = updated.toEpochMillis()

    fun toSManga(iconUrl: String) = SManga.create().apply {
        url = "/$service/user/$id"
        title = name
        author = service.serviceName()
        thumbnail_url = "$iconUrl/icons/$service/$id"
        description = Kemono.PROMPT
    }

    fun toDetailedSManga(
        baseUrl: String,
        iconUrl: String,
        tags: List<KemonoTagDto>,
        announcements: List<KemonoAnnouncementDto>,
        links: List<KemonoCreatorDto>,
    ) = toSManga(iconUrl).apply {
        genre = tags.sortedByDescending { it.postCount }
            .mapNotNull { it.tag.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString()

        description = MarkdownDescription.Builder()
            .field("Service", service.serviceName())
            .field("Handle", publicId?.takeIf { !it.equals(name, ignoreCase = true) })
            .links(
                links.map { "${it.name} (${it.service.serviceName()})" to "$baseUrl/${it.service}/user/${it.id}" },
                label = "Linked Accounts",
            )
            .apply {
                announcements.maxByOrNull { it.addedDate }?.let { announcement ->
                    separator()
                    description(Jsoup.parseBodyFragment(announcement.content, baseUrl).body(), keepNewlines = true)
                }
            }
            .separator()
            .description(Kemono.PROMPT)
            .build()
    }

    companion object {
        fun String.serviceName() = when (this) {
            "fanbox" -> "Pixiv Fanbox"
            "subscribestar" -> "SubscribeStar"
            "dlsite" -> "DLsite"
            "onlyfans" -> "OnlyFans"
            else -> replaceFirstChar { it.uppercase() }
        }
    }
}

@Serializable
class KemonoTagDto(
    val tag: String,
    @SerialName("post_count") val postCount: Int = 0,
)

@Serializable
class KemonoAnnouncementDto(
    val content: String,
    private val added: String? = null,
) {
    val addedDate: Long
        get() = added.parseKemonoDate(ZoneOffset.UTC)
}

@Serializable
class KemonoFancardDto(
    val hash: String,
    val ext: String,
    private val added: String? = null,
) {
    val isValidImage: Boolean
        get() = hash.length >= 4 && ext.removePrefix(".").lowercase() in VALID_IMAGE_EXTENSIONS

    val addedDate: Long
        get() = added.parseKemonoDate(JST_ZONE)

    fun thumbnailUrl(imgUrl: String) = "$imgUrl/thumbnail/data/${hash.take(2)}/${hash.substring(2, 4)}/$hash$ext"
}

@Serializable
class KemonoPostResponseDto(
    val post: KemonoPostDto,
    private val previews: List<KemonoFileDto> = emptyList(),
    private val attachments: List<KemonoFileDto> = emptyList(),
) {
    val servers: Map<String, String>
        get() = (previews + attachments).mapNotNull { file ->
            val path = file.path ?: return@mapNotNull null
            val server = file.server ?: return@mapNotNull null
            path to server
        }.toMap()
}

@Serializable
class KemonoPostDto(
    private val id: String,
    val service: String,
    private val user: String,
    private val title: String? = null,
    private val content: String? = null,
    private val substring: String? = null,
    // Single-post responses return a JSON array; creator post listings return a Postgres array string
    private val tags: JsonElement? = null,
    private val added: String? = null,
    private val published: String? = null,
    private val edited: String? = null,
    private val file: KemonoFileDto? = null,
    private val attachments: List<KemonoFileDto> = emptyList(),
    @SerialName("has_full") val hasFull: Boolean? = null,
    @SerialName("faved_seq") val favedSeq: Long = 0,
) {
    val postPath: String
        get() = "/$service/user/$user/post/$id"

    fun matches(query: String) = query.isEmpty() ||
        title?.contains(query, ignoreCase = true) == true ||
        (content ?: substring)?.contains(query, ignoreCase = true) == true

    private val files: List<KemonoFileDto>
        get() = listOfNotNull(file) + attachments

    val images: List<KemonoFileDto>
        get() = files
            .filter { it.path?.isImageFile() == true }
            .distinctBy { it.path }

    val zips: List<KemonoFileDto>
        get() = files
            .filter { it.path?.endsWith(".zip", ignoreCase = true) == true }
            .distinctBy { it.path }

    fun hasContent(includeZips: Boolean) = images.isNotEmpty() || (includeZips && zips.isNotEmpty())

    fun fillServers(servers: Map<String, String>) {
        files.forEach { if (it.server.isNullOrBlank()) it.server = it.path?.let(servers::get) }
    }

    private val zone: ZoneId
        get() = if (service == "fanbox" || service == "fantia") JST_ZONE else ZoneOffset.UTC

    private fun parseDate(datePref: String): Long {
        val dateStr = when (datePref) {
            Kemono.POST_DATE_ADDED -> added ?: published ?: edited
            Kemono.POST_DATE_EDITED -> edited ?: published ?: added
            else -> published ?: added ?: edited
        }
        return dateStr.parseKemonoDate(zone)
    }

    private fun displayTitle(postDate: Long): String = title.orEmpty().trim().ifBlank {
        val postDateString = if (postDate != 0L) {
            chapterNameDateFormat.format(JavaInstant.ofEpochMilli(postDate).atZone(ZoneId.systemDefault()))
        } else {
            "unknown date"
        }
        "Post from $postDateString"
    }

    fun toSManga(creator: String?, baseUrl: String, imgUrl: String, iconUrl: String, datePref: String) = SManga.create().apply {
        val postDate = parseDate(datePref)
        url = postPath
        title = displayTitle(postDate)
        author = creator ?: service.serviceName()
        description = MarkdownDescription.Builder()
            .description((content ?: substring)?.let { Jsoup.parseBodyFragment(it, baseUrl).body() }, keepNewlines = true)
            .build()
        genre = tags.parseTags()
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString()
        thumbnail_url = images.firstOrNull()?.thumbnailUrl(imgUrl)
            ?: "$iconUrl/icons/$service/$user"
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter(datePref: String) = SChapter.create().apply {
        val postDate = parseDate(datePref)
        url = postPath
        date_upload = postDate
        name = displayTitle(postDate)
    }
}

@Serializable
class KemonoFileDto(
    val path: String? = null,
    var server: String? = null,
    @SerialName("temp_url") private val tempUrl: String? = null,
) {
    val dataUrl: String
        get() = "$server/data${path.orEmpty()}"

    fun thumbnailUrl(imgUrl: String) = "$imgUrl/thumbnail/data${path.orEmpty()}"

    fun imageUrl(imgUrl: String, hasFull: Boolean? = null, fallbackToThumbnail: Boolean = true) = when {
        !server.isNullOrBlank() && (hasFull != false || !fallbackToThumbnail) -> dataUrl
        !tempUrl.isNullOrBlank() -> tempUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.fragment(path)
            ?.build()
            ?.toString()
            ?: thumbnailUrl(imgUrl)
        else -> thumbnailUrl(imgUrl)
    }
}

@Serializable
class KemonoZipPageDto(
    val url: String,
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val method: Int,
    val localHeaderAllowance: Int,
)

internal fun String.isImageFile() = substringAfterLast('.').lowercase() in VALID_IMAGE_EXTENSIONS

private val JST_ZONE: ZoneId = ZoneOffset.ofHours(9)
private val chapterNameDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd 'at' HH:mm:ss", Locale.ENGLISH)
private val VALID_IMAGE_EXTENSIONS = setOf("png", "jpg", "gif", "jpeg", "webp")
private val POSTGRES_ARRAY_ELEMENT_REGEX = Regex("""(?:^|,)\s*(?:"((?:[^"\\]|\\.)*)"\s*|([^,]*))""")
private val POSTGRES_ESCAPE_REGEX = Regex("""\\(.)""")

private fun JsonElement?.parseTags(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.stringOrNull?.trim()?.takeIf(String::isNotEmpty) }
    is JsonPrimitive -> if (isString) {
        val inner = content.trim().removeSurrounding("{", "}")
        if (inner.isEmpty()) {
            emptyList()
        } else {
            POSTGRES_ARRAY_ELEMENT_REGEX.findAll(inner)
                .mapNotNull { match ->
                    val value = match.groups[1]?.value?.replace(POSTGRES_ESCAPE_REGEX, "$1")
                        ?: match.groups[2]?.value
                    value?.trim()?.takeIf(String::isNotEmpty)
                }
                .toList()
        }
    } else {
        emptyList()
    }
    else -> emptyList()
}

private fun String?.parseKemonoDate(zone: ZoneId): Long {
    if (this.isNullOrBlank()) return 0L
    return Instant.tryParse(this).takeIf { it != 0L }
        ?: DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(this, zone)
}

private fun JsonPrimitive?.toEpochMillis(): Long {
    if (this == null) return 0L
    if (isString) return content.parseKemonoDate(ZoneOffset.UTC)
    return doubleOrNull?.times(1000)?.toLong() ?: 0L
}
