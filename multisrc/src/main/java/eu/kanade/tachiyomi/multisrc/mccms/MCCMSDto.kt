package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

internal const val PAGE_SIZE = 30

@Serializable
data class MangaDto(
    val id: String,
    private val name: String,
    private val pic: String,
    private val serialize: String,
    private val author: String,
    private val content: String,
    private val addtime: String,
    val url: String,
    private val tags: List<String>,
) {
    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = name
        author = this@MangaDto.author
        description = content
        genre = tags.joinToString()
        val date = dateFormat.parse(addtime)?.time ?: 0
        val isUpdating = System.currentTimeMillis() - date <= 30L * 24 * 3600 * 1000 // a month
        status = when {
            '连' in serialize || isUpdating -> SManga.ONGOING
            '完' in serialize -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = pic
        initialized = true
    }

    companion object {
        private val dateFormat by lazy { getDateFormat() }
    }
}

@Serializable
class ChapterDto(val id: String, private val name: String, private val link: String) {
    fun toSChapter(date: Long) = SChapter.create().apply {
        url = link
        name = this@ChapterDto.name
        date_upload = date
    }
}

@Serializable
class ChapterDataDto(val id: String, private val addtime: String) {
    val date get() = dateFormat.parse(addtime)?.time ?: 0

    companion object {
        private val dateFormat by lazy { getDateFormat() }
    }
}

@Serializable
class ResultDto<T>(val data: T)

fun getDateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
