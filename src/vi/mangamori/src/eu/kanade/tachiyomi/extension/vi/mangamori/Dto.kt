package eu.kanade.tachiyomi.extension.vi.mangamori

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SearchResponse(
    val data: List<SearchComic>,
)

@Serializable
class SearchComic(
    private val slug: String,
    private val title: String,
    private val thumbnail: String,
    private val author: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SearchComic.title
        thumbnail_url = thumbnail
        author = this@SearchComic.author
    }
}

@Serializable
class ChapterPayload(
    private val chapters: List<ChapterDto>,
    private val rawChapters: List<ChapterDto>,
) {
    fun toSChapterList(slug: String): List<SChapter> = buildList {
        chapters.mapTo(this) { it.toSChapter(slug, "VietSub") }
        rawChapters.mapTo(this) { it.toSChapter(slug, "Raw", raw = true) }
    }.sortedByDescending(SChapter::chapter_number)
}

@Serializable
class ChapterDto(
    private val chapterNumber: Float,
    private val title: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(slug: String, language: String, raw: Boolean = false) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        url = "$slug/$number${if (raw) "?mode=raw" else ""}"
        name = buildString {
            append("Ch. ", number, " (", language, ")")
            title?.takeIf(String::isNotBlank)?.let { append(" - ", it) }
        }
        chapter_number = chapterNumber
        scanlator = language
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ReaderPayload(
    val chapter: ReaderChapter,
    val encryptedMap: String? = null,
    val mapNonce: String? = null,
) {
    val rows = 8
    val cols = 8
}

@Serializable
class ReaderChapter(
    val pages: List<ReaderPage>,
)

@Serializable
class ReaderPage(
    val imageUrl: String,
    val pageOrder: Int,
    val isScrambled: Boolean = false,
    val scrambleRows: Int? = null,
    val scrambleCols: Int? = null,
)
