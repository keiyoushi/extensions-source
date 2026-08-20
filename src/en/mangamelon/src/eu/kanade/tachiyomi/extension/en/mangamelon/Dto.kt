package eu.kanade.tachiyomi.extension.en.mangamelon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ================================ Requests ================================

@Serializable
class MangaListRequest(
    val search: String = "",
    val genre: String = "",
    val lang: String = "en",
    val sort: String = "popular",
    val includeNsfw: Boolean = true,
    val limit: Int = 36,
    val skip: Int = 0,
)

@Serializable
class MangaGetRequest(
    val target: String,
    val withReviews: Boolean = false,
)

@Serializable
class ChapterListRequest(
    val target: String,
    val status: Int = 0,
    val limit: Int = 1000,
    val skip: Int = 0,
    val pending: String = "",
    val force: Boolean = true,
)

@Serializable
class ChapterGetRequest(
    val target: String,
    val all: Boolean = true,
)

// ================================ Responses ================================

@Serializable
class MangaListResponse(
    val list: List<MangaDto>,
    val total: Long = -1,
)

@Serializable
class MangaGetResponse(
    val manga: MangaDto,
)

@Serializable
class ChapterListResponse(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterGetResponse(
    val chapter: ChapterDto,
)

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val cover: String? = null,
    private val desc: String? = null,
    private val status: String? = null,
    private val authors: String? = null,
    private val genres: List<String> = emptyList(),
    private val lang: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = this@toSManga.id
        this.title = this@toSManga.title
        thumbnail_url = cover.orEmpty()
        description = desc
        author = authors
        genre = genres.joinToString()
        this.lang = this@toSManga.lang.orEmpty()
        status = this@toSManga.status.toMangaStatus()
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String,
    private val seq: Int = 0,
    private val updated: String? = null,
    val pages: List<PageDto> = emptyList(),
) {
    fun toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        name = this@toSChapter.title
        url = "$mangaId/${this@toSChapter.id}"
        date_upload = updated.takeUnless { it.isNullOrEmpty() || it.startsWith("0001-") }
            ?.let { Instant.tryParse(it) }
            ?: 0L
    }
}

@Serializable
class PageDto(
    val url: String,
    val seq: Int = 0,
)

private fun String?.toMangaStatus(): Int = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "licensed" -> SManga.LICENSED
    "on hiatus", "hiatus" -> SManga.ON_HIATUS
    "cancelled", "canceled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
