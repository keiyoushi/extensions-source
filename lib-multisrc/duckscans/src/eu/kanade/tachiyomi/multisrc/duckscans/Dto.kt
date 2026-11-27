package eu.kanade.tachiyomi.multisrc.duckscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class SeriesListDto(val data: List<SeriesDto>)

@Serializable
class SeriesDetailsDto(val data: SeriesDto)

@Serializable
class SeriesDto(
    val title: String,
    private val slug: String,
    private val alternativeNames: List<String> = emptyList(),
    private val image: String? = null,
    val views: Long = 0,
    val likes: Long = 0,
    val genre: List<String> = emptyList(),
    val type: String? = null,
    val status: String? = null,
    private val about: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    val releaseDate: String? = null,
    private val recentChapters: List<RecentChapterDto> = emptyList(),
) {
    val lastChapterAt: Long
        get() = recentChapters.maxOfOrNull { Instant.tryParse(it.createdAt) } ?: 0L

    // The site's own search: title, author, alternative names and genres
    fun matches(term: String): Boolean = title.lowercase().contains(term) ||
        author.orEmpty().lowercase().contains(term) ||
        alternativeNames.any { it.lowercase().contains(term) } ||
        genre.any { it.lowercase().contains(term) }

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/seri/$slug"
        title = this@SeriesDto.title.trim()
        thumbnail_url = image?.let { baseUrl.resolve(it)?.toString() }
    }

    fun toSMangaDetails(baseUrl: HttpUrl) = toSManga(baseUrl).apply {
        author = this@SeriesDto.author.toPerson()
        artist = this@SeriesDto.artist.toPerson()
        genre = (listOfNotNull(type) + this@SeriesDto.genre).distinct().joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status?.lowercase()) {
            "devam ediyor", "güncel", "ongoing" -> SManga.ONGOING
            "tamamlandı" -> SManga.COMPLETED
            "durduruldu" -> SManga.ON_HIATUS
            "bırakıldı" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(about)
            .altTitles(alternativeNames.filterNot { it.equals(title, ignoreCase = true) })
            .build()
    }

    // "Bilinmiyor" and "Güncellenecek..." stand for an unknown author ("unknown", "to be updated")
    private fun String?.toPerson() = this?.trim()?.takeUnless { it.isEmpty() || it.lowercase() in NO_PERSON }
}

@Serializable
class RecentChapterDto(val createdAt: String)

@Serializable
class ChapterListDto(val data: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val seriesId: ChapterSeriesDto,
    @SerialName("series_slug") private val seriesSlug: String,
    private val chapterNumber: Float,
    private val title: String? = null,
    private val coinUnlockRequired: Boolean = false,
    private val coinPrice: Int = 0,
    private val earlyAccessDays: Int = 0,
    private val releaseDate: String? = null,
    val pages: List<String> = emptyList(),
    val locked: Boolean = false,
) {
    fun lockedMessage(now: Long): String = if (seriesId.allowUnauthenticated || isPaid(now)) {
        "Unlock this chapter in WebView"
    } else {
        "Log in via WebView to read this chapter"
    }

    // Paid chapters become free earlyAccessDays after their release, as on the site
    private fun isPaid(now: Long): Boolean = coinUnlockRequired && coinPrice > 0 &&
        (earlyAccessDays <= 0 || now < Instant.tryParse(releaseDate) + earlyAccessDays * DAY_MILLIS)

    fun toSChapter(now: Long) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        val lock = if (isPaid(now) || !seriesId.allowUnauthenticated) "🔒 " else ""

        url = "/seri/$seriesSlug/bolum/$number"
        name = lock + (title ?: "Bölüm $number")
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(releaseDate)
    }
}

@Serializable
class ChapterSeriesDto(val allowUnauthenticated: Boolean = true)

@Serializable
class CategoriesDto(val data: List<String>)

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

private val NO_PERSON = setOf("n/a", "bilinmiyor", "güncellenecek...")
