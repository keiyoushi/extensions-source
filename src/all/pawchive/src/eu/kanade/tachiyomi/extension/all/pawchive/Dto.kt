package eu.kanade.tachiyomi.extension.all.pawchive

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlin.time.Instant

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
        updated.isString -> Instant.parseOrNull(updated.content)?.toEpochMilliseconds() ?: 0L
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

        val postDate = dateStr?.let {
            val dateWithTz = if (it.endsWith("Z") || it.contains("+")) {
                it
            } else {
                it + if (serviceName == "Pixiv Fanbox") "+09:00" else "Z"
            }
            Instant.parseOrNull(dateWithTz)?.toEpochMilliseconds()
        } ?: 0L

        url = "/$service/user/$user/post/$id"
        date_upload = postDate

        name = title.ifBlank {
            val postDateString = if (postDate != 0L && dateStr != null) {
                // Strips any unexpected timezone markers and recreates "yyyy-MM-dd 'at' HH:mm:ss" natively
                dateStr.substringBefore("+").substringBefore("Z").replace("T", " at ")
            } else {
                "unknown date"
            }
            "Post from $postDateString"
        }
        chapter_number = -2f
    }

    companion object {
        private val validImageExtensions = setOf("png", "jpg", "gif", "jpeg", "webp")
    }
}

@Serializable
class PawchiveFileDto(val name: String? = null, val path: String? = null) {
    override fun toString() = path + if (name != null) "?f=$name" else ""
}
