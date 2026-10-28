package eu.kanade.tachiyomi.extension.fr.scanhentaimenu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val success: Boolean = false,
    val mangas: List<MangaItem> = emptyList(),
    val pagination: Pagination? = null,
)

@Serializable
class Pagination(
    val current: Int = 1,
    val totalPages: Int = 1,
    val total: Int = 0,
)

@Serializable
class MangaItem(
    val id: String? = null,
    val title: String,
    val slug: String,
    val coverPath: String? = null,
    val type: String? = null,
    val format: String? = null,
    val status: String? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = slug
        title = this@MangaItem.title.trim()
        thumbnail_url = getCoverUrl(baseUrl, coverPath)
    }
}

@Serializable
class MangaDetailResponse(
    val success: Boolean = false,
    val manga: MangaDetail? = null,
)

@Serializable
class MangaDetail(
    val id: String? = null,
    val title: String,
    val alternativeName: String? = null,
    val slug: String,
    val description: String? = null,
    val type: String? = null,
    val format: String? = null,
    val status: String? = null,
    val coverPath: String? = null,
    val genres: List<NamedItem> = emptyList(),
    val authors: List<NamedItem> = emptyList(),
    val artists: List<NamedItem> = emptyList(),
    val tags: List<NamedItem> = emptyList(),
    val chapters: List<ChapterItem> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = slug
        title = this@MangaDetail.title.trim()
        author = authors.joinToString { it.name.trim() }.ifBlank { null }
        artist = artists.joinToString { it.name.trim() }.ifBlank { null }
        description = this@MangaDetail.description?.trim()?.ifBlank { null }
        genre = (genres.map { it.name.trim() } + tags.map { it.name.trim() })
            .filter(String::isNotBlank)
            .distinct()
            .joinToString()
            .ifBlank { null }
        status = when (this@MangaDetail.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        thumbnail_url = getCoverUrl(baseUrl, coverPath)
    }
}

@Serializable
class NamedItem(
    val id: String? = null,
    val name: String,
    val slug: String? = null,
)

@Serializable
class ChapterItem(
    val id: String,
    val chapterNumber: Double? = null,
    val title: String? = null,
    val publishedAt: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        val numberStr = chapterNumber?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        }
        val defaultName = if (numberStr != null) "Chapitre $numberStr" else "Chapitre"
        name = when {
            title.isNullOrBlank() -> defaultName
            numberStr != null && !title.contains(numberStr) && !title.startsWith("Chapitre", ignoreCase = true) -> {
                "$defaultName: ${title.trim()}"
            }
            else -> title.trim()
        }
        chapter_number = chapterNumber?.toFloat() ?: -1f
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ChapterResponse(
    val success: Boolean = false,
    val chapter: ChapterDetail? = null,
)

@Serializable
class ChapterDetail(
    val id: String? = null,
    val pages: List<PageItem> = emptyList(),
)

@Serializable
class PageItem(
    val id: String? = null,
    val pageNumber: Int? = null,
    val filePath: String,
)

internal fun getCoverUrl(baseUrl: String, coverPath: String?): String? {
    if (coverPath.isNullOrBlank()) return null
    if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) return coverPath
    if (coverPath.startsWith("/covers/")) return "$baseUrl$coverPath"
    val path = if (coverPath.startsWith("/")) coverPath else "/$coverPath"
    return "https://reader.hentai.gifts$path"
}

internal fun getPageUrl(filePath: String): String {
    if (filePath.startsWith("http://") || filePath.startsWith("https://")) return filePath
    val path = if (filePath.startsWith("/")) filePath else "/$filePath"
    return "https://reader.hentai.gifts$path"
}
