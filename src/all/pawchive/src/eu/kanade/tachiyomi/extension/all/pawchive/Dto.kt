package eu.kanade.tachiyomi.extension.all.pawchive

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class PawchiveFavoritesDto(
    val id: String,
    val faved_seq: Long,
)

@Serializable
class PawchiveCreatorDto(
    val id: String,
    val name: String,
    val service: String,
    private val updated: JsonPrimitive,
    val favorited: Int = -1,
) {
    @Transient
    var fav: Long = 0L

    val updatedDate get() = when {
        updated.isString -> try {
            dateFormat.get()!!.parse(updated.content)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        else -> (updated.double * 1000).toLong()
    }

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$service/user/$id"
        title = name
        author = service.serviceName()
        thumbnail_url = "$baseUrl/icons/$service/$id"
        description = Pawchive.PROMPT
        initialized = true
    }

    companion object {
        val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
        }

        fun String.serviceName() = when (this) {
            "fanbox" -> "Pixiv Fanbox"
            else -> replaceFirstChar { it.uppercase() }
        }
    }
}

@Serializable
class PawchivePostDto(
    private val id: String,
    private val service: String,
    private val user: String,
    private val title: String,
    private val added: String? = null,
    private val published: String? = null,
    private val edited: String? = null,
    private val file: PawchiveFileDto? = null,
    private val attachments: List<PawchiveFileDto> = emptyList(),
) {
    val images: List<String>
        get() = (listOfNotNull(file) + attachments)
            .filter { it.path != null && it.path.substringAfterLast('.').lowercase() in validImageExtensions }
            .distinctBy { it.path }
            .map { it.toString() }

    fun toSChapter(serviceName: String?, datePref: String) = SChapter.create().apply {
        val dateStr = when (datePref) {
            "added" -> added ?: published ?: edited
            "edited" -> edited ?: published ?: added
            else -> published ?: added ?: edited // Default to published
        }

        val format = dateFormat.get()!!

        // Prevents leaking SimpleDateFormat mutations across concurrently fetched mangas
        format.timeZone = if (serviceName == "Pixiv Fanbox") TimeZone.getTimeZone("GMT+09:00") else TimeZone.getTimeZone("GMT")

        val postDate = try {
            if (dateStr != null) format.parse(dateStr)?.time ?: 0L else 0L
        } catch (_: Exception) {
            0L
        }

        url = "/$service/user/$user/post/$id"
        date_upload = postDate
        name = title.ifBlank {
            val postDateString = if (postDate != 0L) {
                chapterNameDateFormat.get()!!.apply { timeZone = format.timeZone }.format(postDate)
            } else {
                "unknown date"
            }
            "Post from $postDateString"
        }
        chapter_number = -2f
    }

    companion object {
        private val validImageExtensions = setOf("png", "jpg", "gif", "jpeg", "webp")

        private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        }

        private val chapterNameDateFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss", Locale.ENGLISH)
        }
    }
}

@Serializable
class PawchiveFileDto(val name: String? = null, val path: String? = null) {
    override fun toString() = path + if (name != null) "?f=$name" else ""
}
