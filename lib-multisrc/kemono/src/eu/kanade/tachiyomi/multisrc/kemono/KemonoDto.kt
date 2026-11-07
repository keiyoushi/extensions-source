package eu.kanade.tachiyomi.multisrc.kemono

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import org.jsoup.Jsoup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class KemonoFavoritesDto(
    val id: String,
    val name: String,
    val service: String,
    val faved_seq: Long,
)

@Serializable
class KemonoCreatorDto(
    val id: String,
    val name: String,
    val service: String,
    private val updated: JsonPrimitive,
    val favorited: Int = -1,
) {
    var fav: Long = 0
    val updatedDate get() = when {
        updated.isString -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(updated.content)
        else -> (updated.double * 1000).toLong()
    }

    fun toSManga(imgCdnUrl: String) = SManga.create().apply {
        url = "/$service/user/$id" // should be /server/ for Discord but will be filtered anyway
        title = name
        author = service.serviceName()
        thumbnail_url = "$imgCdnUrl/icons/$service/$id"
        description = Kemono.PROMPT
        initialized = true
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
class KemonoPostDtoWrapped(
    val post: KemonoPostDto,
)

@Serializable
class KemonoPostDto(
    private val id: String,
    private val service: String,
    private val user: String,
    private val title: String,
    private val content: String? = null,
    private val tags: List<String>? = null,
    private val added: String? = null,
    private val published: String?,
    private val edited: String? = null,
    private val file: KemonoFileDto,
    private val attachments: List<KemonoAttachmentDto>,
) {
    private val imageFiles: List<KemonoAttachmentDto>
        get() = buildList(attachments.size + 1) {
            if (file.path != null) add(KemonoAttachmentDto(file.name, file.path))
            addAll(attachments)
        }.filter {
            when (it.path.substringAfterLast('.').lowercase()) {
                "png", "jpg", "gif", "jpeg", "webp" -> true
                else -> false
            }
        }.distinctBy { it.path }

    val images: List<String>
        get() = imageFiles.map { it.toString() }

    private fun date(zone: ZoneId) = DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(edited ?: published ?: added, zone)

    private fun displayTitle(zone: ZoneId) = title.trim().ifBlank {
        val postDate = date(zone)
        val postDateString = when {
            postDate != 0L -> chapterNameDateFormat.format(Instant.ofEpochMilli(postDate).atZone(ZoneId.systemDefault()))
            else -> "unknown date"
        }

        "Post from $postDateString"
    }

    fun toSManga(creator: String?, baseUrl: String, imgCdnUrl: String, zone: ZoneId) = SManga.create().apply {
        url = "/$service/user/$user/post/$id"
        title = displayTitle(zone)
        author = creator
        description = MarkdownDescription.Builder()
            .description(content?.let { Jsoup.parseBodyFragment(it, baseUrl).body() }, keepNewlines = true)
            .build()
        genre = tags?.takeIf { it.isNotEmpty() }?.joinToString()
        thumbnail_url = imageFiles.firstOrNull()
            ?.let { "$imgCdnUrl/thumbnail/data${it.path}" }
            ?: "$imgCdnUrl/icons/$service/$user"
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter(zone: ZoneId) = SChapter.create().apply {
        url = "/$service/user/$user/post/$id"
        date_upload = date(zone)
        name = displayTitle(zone)
        chapter_number = -2f
    }
}

@Serializable
class KemonoFileDto(val name: String? = null, val path: String? = null)

// name might have ".jpe" extension for JPEG, path might have ".m4v" extension for MP4
@Serializable
class KemonoAttachmentDto(var name: String? = null, val path: String) {
    override fun toString() = path + if (name != null) "?f=$name" else ""
}

private val chapterNameDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd 'at' HH:mm:ss", Locale.ENGLISH)
