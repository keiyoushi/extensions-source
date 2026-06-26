package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

@Serializable
class Mangas(
    val series: List<Manga>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class Manga(
    val title: String,
    val slug: String,
    val coverPath: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@Manga.title
        thumbnail_url = "$baseUrl/api/cover/${coverPath.trim('/')}"
        url = "/series/$slug"
    }
}

@Serializable
class ChapterList(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    val id: Int,
    val number: Float,
    val title: String?,
    val isVipOnly: Boolean,
    val publishedAt: String?,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = buildString {
            if (isVipOnly) append("🔒 ")
            append(title ?: "Capítulo ${number.toInt()}")
        }
        chapter_number = number
        date_upload = publishedAt?.removePrefix("\$D")?.let { dateFormat.tryParse(it) } ?: 0L
        url = "$id"
    }
}

@Serializable
class PagesList(
    val pages: List<PageItem>,
)

@Serializable
class PageItem(
    val i: Int,
    val u: String,
)
