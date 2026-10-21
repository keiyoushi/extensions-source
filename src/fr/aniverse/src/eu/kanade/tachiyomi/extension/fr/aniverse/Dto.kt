package eu.kanade.tachiyomi.extension.fr.aniverse

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.EncodeDefault
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
class SearchDto(
    val data: List<MangaItemDto>,
    private val total: Int,
    private val page: Int,
    private val pageSize: Int,
) {
    val hasNextPage get() = page * pageSize < total
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
    private val mangaId: String,
    private val publicId: String,
    private val title: String,
    private val image: String? = null,
    private val chapters: List<ReaderChapterDto>,
    val pages: List<ReaderPageDto>,
    val locked: Boolean,
) {
    val slug get() = publicId

    /** Position of a chapter in the site's ascending chapter list, or -1. */
    fun indexOf(chapterId: String) = chapters.indexOfFirst { it.id == chapterId }

    fun chapterIdAt(index: Int) = chapters[index].id

    fun chapterLabelAt(index: Int) = "$title ch. ${chapters[index].number}"

    // Mirrors the entry the site's own reader stores in the account history.
    fun toHistoryEntry(index: Int): HistoryEntryDto {
        val target = chapters[index]
        return HistoryEntryDto(
            mangaId = mangaId,
            publicId = publicId,
            title = title,
            image = image,
            chapterId = target.id,
            chapterNumber = target.number,
            chapterKind = target.kind?.takeIf { it == "tome" },
            chapterTitle = target.title.orEmpty(),
            page = 1,
            pageCount = (target.pageCount ?: 1).coerceAtLeast(1),
            updatedAt = System.currentTimeMillis(),
            chapterIndex = index + 1,
            totalChapters = chapters.size,
        )
    }
}

@Serializable
class ReaderChapterDto(
    val id: String,
    val number: String,
    val title: String? = null,
    val kind: String? = null,
    val pageCount: Int? = null,
)

@Serializable
class ReaderPageDto(val src: String)

@Serializable
class UserMeDto(val user: UserDto?)

@Serializable
class UserDto(
    private val name: String,
    private val displayUsername: String? = null,
) {
    val displayName get() = displayUsername ?: name
}

@Serializable
class ProgressRequestDto(private val chapterId: String)

@Serializable
class HistorySyncRequestDto(private val changes: List<HistoryChangeDto>)

@Serializable
class HistoryChangeDto(
    private val kind: String,
    private val entry: HistoryEntryDto,
)

@Serializable
class HistoryEntryDto(
    private val mangaId: String,
    private val publicId: String,
    private val title: String,
    // Absent values are left out of the payload, like the site does.
    @EncodeDefault(EncodeDefault.Mode.NEVER) private val image: String? = null,
    private val chapterId: String,
    private val chapterNumber: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) private val chapterKind: String? = null,
    private val chapterTitle: String,
    private val page: Int,
    private val pageCount: Int,
    private val updatedAt: Long,
    private val chapterIndex: Int,
    private val totalChapters: Int,
)
