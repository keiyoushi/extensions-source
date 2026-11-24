package eu.kanade.tachiyomi.extension.all.bookorbit

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class LoginRequestDto(
    private val username: String,
    private val password: String,
    private val clientKind: String,
    private val deviceLabel: String,
)

@Serializable
class RefreshRequestDto(private val refreshToken: String)

@Serializable
class CredentialsDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
class LibraryDto(
    val id: Int,
    val name: String,
)

@Serializable
class SeriesPageDto(
    val items: List<SeriesSummaryDto>,
    val total: Int,
)

@Serializable
class SeriesSummaryDto(
    private val id: Int,
    private val name: String,
    private val authors: List<String>,
    private val coverBookIds: List<Int>,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = name
        author = authors.joinToString()
        thumbnail_url = coverBookIds.firstOrNull()?.let { thumbnailUrl(baseUrl, it) }
    }
}

@Serializable
class SeriesBooksPageDto(
    val items: List<BookCardDto>,
    val total: Int,
    val seriesInfo: SeriesInfoDto,
)

@Serializable
class SeriesInfoDto(
    val name: String,
    val authors: List<String>,
)

@Serializable
class BookCardDto(
    val id: Int,
    private val title: String?,
    private val seriesIndex: String?,
    private val files: List<BookFileDto>,
    val genres: List<String>,
    private val addedAt: String,
) {
    val isComic get() = files.any { it.isComic }

    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = listOfNotNull(seriesIndex?.let { "#$it" }, title).joinToString(" - ")
        chapter_number = seriesIndex?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(addedAt)
    }
}

@Serializable
class BookDetailDto(val files: List<BookFileDto>)

@Serializable
class BookFileDto(
    val id: Int,
    private val format: String?,
) {
    val isComic get() = format in COMIC_FORMATS
}

@Serializable
class PageCountDto(val pageCount: Int)

fun thumbnailUrl(baseUrl: String, bookId: Int) = "$baseUrl/api/v1/books/$bookId/thumbnail"

// Formats BookOrbit's comic reader can serve page by page.
private val COMIC_FORMATS = setOf("cbz", "cbr", "cb7")
