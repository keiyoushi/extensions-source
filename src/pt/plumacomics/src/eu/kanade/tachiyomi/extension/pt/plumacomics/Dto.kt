package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class Mangas(
    val series: List<Manga>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class Manga(
    val id: Long,
    val title: String,
    val slug: String,
    val status: String? = null,
    val coverPath: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@Manga.title
        thumbnail_url = coverPath.takeUnless(String?::isNullOrBlank)?.let { "$baseUrl/api/cover/${it.trim('/')}" }
        status = when (this@Manga.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        url = id.toString()
        memo = buildJsonObject {
            put("slug", slug)
        }
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
        date_upload = Instant.tryParse(publishedAt)
        url = id.toString()
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

@Serializable
class Series(
    val seriesId: Long,
    val seriesSlug: String,
)
