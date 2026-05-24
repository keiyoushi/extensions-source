package eu.kanade.tachiyomi.extension.es.lmtoonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.TimeZone

private val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class MangaList(
    val mangas: List<Manga>,
)

@Serializable
class MangaDetails(
    val manga: Manga,
)

@Serializable
class Manga(
    val slug: String,
    val title: String,
    val alternativeTitles: List<String>? = null,
    private val description: String? = null,
    private val coverImage: String? = null,
    val isAdult: Boolean = false,
    val type: String? = null,
    val status: String? = null,
    val demographic: String? = null,
    val genres: List<String>? = null,
    val author: String? = null,
    val artist: String? = null,
    val latestChapterCreatedAt: String? = null,
    val totalViews: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@Manga.title
        description = buildString {
            append(this@Manga.description)
            append("\n\n")
            if (!alternativeTitles.isNullOrEmpty()) {
                append("Nombres alternativos: ")
                append(alternativeTitles.joinToString())
            }
        }
        thumbnail_url = coverImage
        genre = buildList {
            add(type?.replaceFirstChar { it.uppercase() })
            genres?.let { addAll(it) }
        }.joinToString()
        this@Manga.author?.let { author = it }
        this@Manga.artist?.let { artist = it }
        status = parseStatus(this@Manga.status)
        initialized = true
    }

    private fun parseStatus(text: String?): Int = when (text?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "paused" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterList(
    val chapters: List<Chapter>,
    val manga: Manga,
)

@Serializable
class Chapter(
    private val slug: String,
    private val number: Float,
    private val createdAt: String,
    val pages: List<String>? = emptyList(),
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = "Cap. ${number.toString().removeSuffix(".0")}"
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class ChapterPages(
    val chapter: Chapter,
)
