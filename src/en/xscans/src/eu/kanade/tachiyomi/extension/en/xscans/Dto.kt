package eu.kanade.tachiyomi.extension.en.xscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class MangaResponseDto(
    private val manga: List<MangaDto> = emptyList(),
    private val pagination: PaginationDto? = null,
) {
    val mangas get() = manga
    val hasNextPage get() = pagination?.hasNextPage ?: false
}

@Serializable
class PaginationDto(
    private val hasMore: Boolean = false,
) {
    val hasNextPage get() = hasMore
}

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val coverImage: String? = null,
    private val description: String? = null,
    private val authors: List<String> = emptyList(),
    private val artists: List<String> = emptyList(),
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
    private val demographics: List<String> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverImage?.let { if (it.startsWith("http")) it else baseUrl + it }
        description = this@MangaDto.description
        author = authors.joinToString()
        artist = artists.joinToString()
        status = parseStatus(this@MangaDto.status)
        genre = (genres + demographics).filter { it.isNotBlank() }.joinToString()
    }

    fun getChapters(showLocked: Boolean): List<SChapter> = chapters
        .filter { showLocked || !it.isLocked }
        .map {
            SChapter.create().apply {
                url = "/api/manga/$slug/chapters?number=${it.number.toString().removeSuffix(".0")}"
                val chapterName = it.title.takeIf { t -> !t.isNullOrBlank() }
                    ?: "Chapter ${it.number.toString().removeSuffix(".0")}"

                name = if (it.isLocked) "🔒 $chapterName" else chapterName
                date_upload = dateFormat.tryParse(it.publishDate)
                chapter_number = it.number
            }
        }
        .sortedByDescending { it.chapter_number }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled", "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterDto(
    val number: Float,
    val title: String? = null,
    val publishDate: String? = null,
    val isLocked: Boolean = false,
)

@Serializable
class NextJsDataDto(
    val props: NextJsPropsDto,
)

@Serializable
class NextJsPropsDto(
    val pageProps: PagePropsDto,
)

@Serializable
class PagePropsDto(
    val initialManga: MangaDto,
)

@Serializable
class PagesResponseDto(
    val images: List<String> = emptyList(),
)
