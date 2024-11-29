package eu.kanade.tachiyomi.multisrc.kemono

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import java.text.SimpleDateFormat
import java.util.Locale
@Serializable
class KemonoFavouritesDto(
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
        updated.isString -> dateFormat.parse(updated.content)?.time ?: 0
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
        private val dateFormat by lazy { getApiDateFormat() }

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
    private val added: String,
    private val published: String?,
    private val edited: String?,
    private val file: KemonoFileDto,
    private val attachments: List<KemonoAttachmentDto>,
) {
    val images: List<String>
        get() = buildList(attachments.size + 1) {
            if (file.path != null) add(KemonoAttachmentDto(file.name!!, file.path))
            addAll(attachments)
        }.filter {
            when (it.path.substringAfterLast('.').lowercase()) {
                "png", "jpg", "gif", "jpeg", "webp" -> true
                else -> false
            }
        }.distinctBy { it.path }.map { it.toString() }

    fun toSChapter() = SChapter.create().apply {
        val postDate = dateFormat.parse(edited ?: published ?: added)

        url = "/$service/user/$user/post/$id"
        date_upload = postDate?.time ?: 0
        name = title.ifBlank {
            val postDateString = when {
                postDate != null && postDate.time != 0L -> chapterNameDateFormat.format(postDate)
                else -> "unknown date"
            }

            "Post from $postDateString"
        }
        chapter_number = -2f
    }

    companion object {
        val dateFormat by lazy { getApiDateFormat() }
        val chapterNameDateFormat by lazy { getChapterNameDateFormat() }
    }
}

@Serializable
class KemonoFileDto(val name: String? = null, val path: String? = null)

// name might have ".jpe" extension for JPEG, path might have ".m4v" extension for MP4
@Serializable
class KemonoAttachmentDto(val name: String, val path: String) {
    override fun toString() = "$path?f=$name"
}

private fun getApiDateFormat() =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)

private fun getChapterNameDateFormat() =
    SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss", Locale.ENGLISH)
