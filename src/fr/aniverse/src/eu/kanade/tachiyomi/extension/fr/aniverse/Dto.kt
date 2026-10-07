package eu.kanade.tachiyomi.extension.fr.aniverse

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

private val brRegex = """\s*<br\s*/?>\s*""".toRegex()

@Serializable
class MangaListDto(
    val items: List<MangaItemDto>,
    val hasNextPage: Boolean,
)

@Serializable
class MangaItemDto(
    private val slug: String,
    private val title: String,
    private val image: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaItemDto.title
        thumbnail_url = image
    }
}

@Serializable
class MangaPageDto(
    val manga: MangaDto,
    val chapters: List<ChapterDto>,
)

@Serializable
class MangaDto(
    val slug: String,
    private val title: TitleDto,
    private val description: String? = null,
    private val status: String? = null,
    private val authors: List<String> = emptyList(),
    private val artists: List<String> = emptyList(),
    private val genres: List<String> = emptyList(),
    private val coverImage: CoverDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title.userPreferred
        thumbnail_url = coverImage?.large
        description = this@MangaDto.description?.replace(brRegex, "\n")?.trim()
        author = authors.joinToString().ifEmpty { null }
        artist = artists.joinToString().ifEmpty { null }
        genre = genres.joinToString()
        status = when (this@MangaDto.status) {
            "RELEASING" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class TitleDto(val userPreferred: String)

@Serializable
class CoverDto(val large: String)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String,
    private val title: String? = null,
    private val updatedAt: String,
    private val premiumUntil: String? = null,
) {
    // Locked chapters redirect to the sign-in page until premiumUntil has passed.
    val isLocked get() = Instant.tryParse(premiumUntil) > Clock.System.now().toEpochMilliseconds()

    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$id"
        name = buildString {
            append("Chapitre ").append(number)
            title?.takeIf { it.isNotBlank() }?.let { append(" - ").append(it) }
        }
        date_upload = Instant.tryParse(updatedAt)
        chapter_number = number.toFloatOrNull() ?: -1f
    }
}

@Serializable
class ReaderDto(
    val pages: List<ReaderPageDto>,
    val locked: Boolean,
)

@Serializable
class ReaderPageDto(val src: String)
