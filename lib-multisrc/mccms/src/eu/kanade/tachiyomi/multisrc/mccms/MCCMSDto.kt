package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Entities
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Serializable
class MangaDto(
    private val id: String,
    private val name: String,
    private val pic: String,
    private val serialize: String,
    private val author: String,
    private val content: String,
    private val addtime: String,
    private val url: String,
    private val tags: List<String>,
) {
    val cleanUrl get() = url.removePathPrefix()

    fun toSManga() = SManga.create().apply {
        url = cleanUrl
        title = Entities.unescape(name)
        author = this@MangaDto.author.ifEmpty { null }?.let(Entities::unescape)
        description = MarkdownDescription.Builder().description(Entities.unescape(content)).build()
        genre = tags.joinToString().ifEmpty { null }?.let(Entities::unescape)
        status = when (serialize) {
            "连载", "连载中", "連載中", "En cours", "OnGoing" -> SManga.ONGOING
            "完结", "已完结", "已完結", "Terminé", "Complete", "Complété" -> SManga.COMPLETED
            else -> if (isUpdating(addtime)) SManga.ONGOING else SManga.UNKNOWN
        }
        thumbnail_url = "$pic#$id"
        initialized = true
    }

    companion object {
        private fun isUpdating(dateStr: String): Boolean {
            val date = DATE_FORMAT.tryParseDateTime(dateStr).takeIf { it != 0L } ?: return false
            return Clock.System.now().toEpochMilliseconds() - date <= 30.days.inWholeMilliseconds
        }
    }
}

@Serializable
class ChapterDto(val id: String, private val name: String, private val link: String) {
    fun toSChapter(date: Long) = SChapter.create().apply {
        url = link.removePathPrefix()
        name = Entities.unescape(this@ChapterDto.name)
        date_upload = date
    }
}

@Serializable
class ChapterDataDto(val id: String, private val addtime: String) {
    val date get() = DATE_FORMAT.tryParseDateTime(addtime)
}

@Serializable
class ResultDto<T>(val data: T)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

@Serializable
class ImageListDto(val images: List<String>)
