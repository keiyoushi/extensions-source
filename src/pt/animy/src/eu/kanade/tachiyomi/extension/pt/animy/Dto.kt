package eu.kanade.tachiyomi.extension.pt.animy

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class MangaListResponse(
    val items: List<MangaItem>,
    val total: Int,
)

@Serializable
class GenresDto(
    val genres: List<String> = emptyList(),
)

@Serializable
class MangaRef(
    private val id: Long,
    private val t: String,
) {
    // Left uninitialized so the app still fetches details (and chapters) after a URL search
    fun toSManga(): SManga {
        val manga = SManga.create()
        manga.url = id.toString()
        manga.title = t
        return manga
    }
}

@Serializable
class MangaItem(
    private val id: Long,
    private val t: String,
    private val cover: String? = null,
    private val desc: String? = null,
    private val g: List<String> = emptyList(),
    private val status: String? = null,
    private val au: String? = null,
    private val type: String? = null,
) {
    fun toSManga(): SManga {
        val manga = SManga.create()
        manga.url = id.toString()
        manga.title = t
        manga.thumbnail_url = cover
        manga.description = desc
        manga.author = au
        // "manhwa"/"manhua" kept in genre; plain "manga" is redundant for a manga source
        manga.genre = if (type != null && type != "manga") (g + type).joinToString() else g.joinToString()
        manga.status = parseStatus(status)
        return manga
    }
}

@Serializable
class MangaDetails(
    private val id: Long,
    private val t: String,
    private val cover: String? = null,
    private val desc: String? = null,
    private val g: List<String> = emptyList(),
    private val status: String? = null,
    private val au: String? = null,
    private val type: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(): SManga {
        val manga = SManga.create()
        manga.url = id.toString()
        manga.title = t
        manga.thumbnail_url = cover
        manga.description = desc
        manga.author = au
        manga.genre = if (type != null && type != "manga") (g + type).joinToString() else g.joinToString()
        manga.status = parseStatus(status)
        manga.initialized = true
        return manga
    }
}

private fun parseStatus(status: String?): Int = when (status) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    else -> SManga.UNKNOWN
}

@Serializable
class ChapterDto(
    private val number: Double,
    private val date: String? = null,
) {
    fun toSChapter(mangaId: Long): SChapter {
        val chapter = SChapter.create()
        val n = number.toString().removeSuffix(".0")
        chapter.url = "$mangaId/$n"
        chapter.name = "Capítulo $n"
        chapter.chapter_number = number.toFloat()
        chapter.date_upload = dateFormat.tryParseDate(date, ZoneId.of("America/Sao_Paulo"))
        return chapter
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    }
}

@Serializable
class ChapterDetails(
    private val pages: List<PageDto> = emptyList(),
) {
    fun toPageList() = pages.map { Page(it.number - 1, imageUrl = it.imageUrl) }
}

@Serializable
class PageDto(
    val number: Int,
    val imageUrl: String,
)
