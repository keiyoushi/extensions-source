package eu.kanade.tachiyomi.extension.all.pawchive

import eu.kanade.tachiyomi.extension.all.pawchive.PawchiveCreatorDto.Companion.serviceName
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
class PawchiveFavoritesDto(
    val id: String,
    val service: String,
    @SerialName("faved_seq") val favedSeq: Long,
)

@Serializable
class PawchiveCreatorDto(
    val id: String,
    val name: String,
    val service: String,
    @SerialName("public_id") val publicId: String?,
    private val indexed: JsonPrimitive?,
    private val updated: JsonPrimitive?,
    val favorited: Int = -1,
) {
    val indexedDate get() = indexed.toEpochMillis()
    val updatedDate get() = updated.toEpochMillis()

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$service/user/$id"
        title = name
        author = service.serviceName()
        thumbnail_url = "$baseUrl/icons/$service/$id"
        description = Pawchive.PROMPT
    }

    fun toDetailedSManga(
        baseUrl: String,
        tags: List<PawchiveTagDto>,
        announcements: List<PawchiveAnnouncementDto>,
        links: List<PawchiveCreatorDto>,
    ) = toSManga(baseUrl).apply {
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
            .description(Pawchive.PROMPT)
            .build()
    }

    companion object {
        fun String.serviceName() = when (this) {
            "fanbox" -> "Pixiv Fanbox"
            else -> replaceFirstChar { it.uppercase() }
        }
    }
}

@Serializable
class PawchiveTagDto(
    val tag: String,
    @SerialName("post_count") val postCount: Int = 0,
)

@Serializable
class PawchiveAnnouncementDto(
    val content: String,
    private val added: String?,
) {
    val addedDate: Long
        get() = added.parsePawchiveDate(ZoneOffset.UTC)
}

@Serializable
class PawchiveFancardDto(
    val hash: String,
    val ext: String,
    private val added: String?,
) {
    val isValidImage: Boolean
        get() = hash.length >= 4 && ext.removePrefix(".").lowercase() in VALID_IMAGE_EXTENSIONS

    val addedDate: Long
        get() = added.parsePawchiveDate(FANBOX_ZONE)

    fun thumbnailUrl(imgUrl: String) = "$imgUrl/thumbnail/data/${hash.take(2)}/${hash.substring(2, 4)}/$hash$ext"
}

@Serializable
class PawchivePostDto(
    private val id: String,
    val service: String,
    private val user: String,
    private val title: String?,
    private val content: String?,
    private val substring: String?,
    // Single-post responses return a JSON array; creator post listings return a Postgres array string
    private val tags: JsonElement?,
    private val added: String?,
    private val published: String?,
    private val edited: String?,
    private val file: PawchiveFileDto?,
    private val attachments: List<PawchiveFileDto> = emptyList(),
    @SerialName("has_full") val hasFull: Boolean?,
) {
    val images: List<PawchiveFileDto>
        get() = (listOfNotNull(file) + attachments)
            .filter {
                val path = it.path
                !path.isNullOrBlank() && path.substringAfterLast('.').lowercase() in VALID_IMAGE_EXTENSIONS
            }
            .distinctBy { it.path }

    private val zone: ZoneId
        get() = if (service == "fanbox") FANBOX_ZONE else ZoneOffset.UTC

    private fun parseDate(datePref: String): Long {
        val dateStr = when (datePref) {
            Pawchive.POST_DATE_ADDED -> added ?: published ?: edited
            Pawchive.POST_DATE_EDITED -> edited ?: published ?: added
            else -> published ?: added ?: edited
        }
        return dateStr.parsePawchiveDate(zone)
    }

    private fun displayTitle(postDate: Long): String = title.orEmpty().trim().ifBlank {
        val postDateString = if (postDate != 0L) {
            chapterNameDateFormat.format(JavaInstant.ofEpochMilli(postDate).atZone(ZoneId.systemDefault()))
        } else {
            "unknown date"
        }
        "Post from $postDateString"
    }

    fun toSManga(creator: String?, baseUrl: String, imgUrl: String, datePref: String) = SManga.create().apply {
        val postDate = parseDate(datePref)
        url = "/$service/user/$user/post/$id"
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
            ?: "$baseUrl/icons/$service/$user"
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter(datePref: String) = SChapter.create().apply {
        val postDate = parseDate(datePref)
        url = "/$service/user/$user/post/$id"
        date_upload = postDate
        name = displayTitle(postDate)
    }
}

@Serializable
class PawchiveFileDto(
    val path: String?,
    private val server: String?,
    @SerialName("temp_url") private val tempUrl: String?,
) {
    fun thumbnailUrl(imgUrl: String) = "$imgUrl/thumbnail/data${path.orEmpty()}"

    fun imageUrl(imgUrl: String, hasFull: Boolean? = null, fallbackToThumbnail: Boolean = true) = when {
        !server.isNullOrBlank() && (hasFull != false || !fallbackToThumbnail) -> "$server/data${path.orEmpty()}"
        !tempUrl.isNullOrBlank() -> tempUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.fragment(path)
            ?.build()
            ?.toString()
            ?: thumbnailUrl(imgUrl)
        else -> thumbnailUrl(imgUrl)
    }
}

private val FANBOX_ZONE: ZoneId = ZoneOffset.ofHours(9)
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

private fun String?.parsePawchiveDate(zone: ZoneId): Long {
    if (this.isNullOrBlank()) return 0L
    return Instant.tryParse(this).takeIf { it != 0L }
        ?: DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(this, zone)
}

private fun JsonPrimitive?.toEpochMillis(): Long {
    if (this == null) return 0L
    if (isString) return content.parsePawchiveDate(ZoneOffset.UTC)
    return doubleOrNull?.times(1000)?.toLong() ?: 0L
}
