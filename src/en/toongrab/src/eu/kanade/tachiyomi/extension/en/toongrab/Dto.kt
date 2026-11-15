package eu.kanade.tachiyomi.extension.en.toongrab

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class BrowseResponse(
    val data: List<BrowseManga>,
    private val totalPages: Int,
) {
    fun hasNextPage(page: Int) = page < totalPages
}

@Serializable
class BrowseManga(
    private val slug: String,
    private val title: String,
    private val coverUrl: String,
) {
    fun toSManga(): SManga {
        val manga = SManga.create()
        manga.url = slug
        manga.title = title
        manga.thumbnail_url = coverUrl
        return manga
    }
}

@Serializable
class MangaDetails(
    private val title: String,
    private val coverUrl: String,
    private val description: String? = null,
    private val genres: List<String> = emptyList(),
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val type: String? = null,
) {
    fun toSManga(slug: String): SManga {
        val manga = SManga.create()
        manga.url = slug
        manga.title = title
        manga.thumbnail_url = coverUrl
        manga.description = description
        manga.author = author
        manga.artist = artist
        manga.genre = (genres + listOfNotNull(type)).joinToString()
        manga.status = when (status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        return manga
    }
}

@Serializable
class ChapterListResponse(
    val chapters: List<ChapterDto>,
    val totalPages: Int,
)

@Serializable
class ChapterDto(
    private val chapterNumber: Double,
    private val title: String? = null,
    private val createdAt: String? = null,
) {
    private val number get() = chapterNumber.toString().removeSuffix(".0")

    fun toSChapter(slug: String): SChapter {
        val chapter = SChapter.create()
        chapter.url = "/manga/$slug/chapter-$number"
        chapter.name = "Chapter $number" + (title?.let { " - $it" } ?: "")
        chapter.chapter_number = chapterNumber.toFloat()
        chapter.date_upload = Instant.tryParse(createdAt)
        return chapter
    }
}

@Serializable
class ChapterImages(
    private val images: List<String>,
) {
    fun toPageList() = images.mapIndexed { index, imageUrl ->
        Page(index, imageUrl = imageUrl)
    }
}
