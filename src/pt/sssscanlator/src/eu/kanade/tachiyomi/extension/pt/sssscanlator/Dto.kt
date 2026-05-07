package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class LibraryResponseDto(
    val data: List<LibraryMangaDto> = emptyList(),
    val pagination: LibraryPaginationDto = LibraryPaginationDto(),
)

@Serializable
class LibraryPaginationDto(
    val page: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
class LibraryMangaDto(
    val title: String,
    val cover: String? = null,
    val slug: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@LibraryMangaDto.title
        thumbnail_url = cover?.takeUnless(String::isBlank)
        url = "/obra/$slug"
    }
}

@Serializable
class SeriesPayloadDto(
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val coverImage: String? = null,
    val chapters: List<SeriesChapterDto> = emptyList(),
    val slug: String,
)

@Serializable
class SeriesChapterDto(
    val number: Double,
    val title: String? = null,
    val releaseDate: String? = null,
    @SerialName("id")
    val chapterId: String,
    val releaseAt: String? = null,
) {
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        val chapterNumberLabel = number.toChapterNumberString()

        url = "/ler/$mangaSlug/$chapterNumberLabel?chapterId=$chapterId"
        name = title?.takeUnless { it.isBlank() } ?: "Capítulo $chapterNumberLabel"
        chapter_number = number.toFloat()
        date_upload = parseChapterDate(releaseAt, releaseDate)
    }

    companion object {
        private val RELEASE_AT_MILLIS by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        private val RELEASE_AT_SECONDS by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        private val RELEASE_DATE by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        }

        private fun parseChapterDate(releaseAt: String?, releaseDate: String?): Long {
            RELEASE_AT_MILLIS.tryParse(releaseAt).takeIf { it != 0L }?.let { return it }
            RELEASE_AT_SECONDS.tryParse(releaseAt).takeIf { it != 0L }?.let { return it }
            return RELEASE_DATE.tryParse(releaseDate)
        }
    }
}

fun Double.toChapterNumberString(): String = toString().removeSuffix(".0")
