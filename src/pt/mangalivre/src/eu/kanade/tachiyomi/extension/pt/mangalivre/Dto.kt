package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.obj
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Serializable
class WrapperDto(
    val mangas: List<MangaDto>,
    private val pagination: PaginationDto,
) {
    val hasNextPage get() = pagination.hasNextPage
}

@Serializable
class PaginationDto(
    val hasNextPage: Boolean,
)

@Serializable
class GenresDto(
    val genres: List<String>?,
)

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val coverUrl: String?,
    private val type: String?,
    private val authors: List<String>?,
    private val artists: List<String>?,
    private val genres: List<String>?,
    private val description: String?,
    private val alternativeTitle: String?,
    private val rating: Double?,
    private val voteCount: Int?,
    private val releaseYear: String?,
    private val chapters: List<ChapterDto>?,
    private val status: String?,
) {
    fun toSManga(useAlternativeTitle: Boolean) = SManga.create().apply {
        val shownTitle = alternativeTitle?.takeIf { useAlternativeTitle && it.isNotBlank() } ?: this@MangaDto.title
        url = id
        title = shownTitle
        thumbnail_url = coverUrl
        author = authors?.joinToString()
        artist = artists?.joinToString()
        genre = (listOfNotNull(type) + genres.orEmpty()).distinct().joinToString().ifEmpty { null }
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(rating?.takeIf { voteCount != 0 }, max = 5, votes = voteCount)
            .info(type, releaseYear)
            .separator()
            .description(this@MangaDto.description)
            .altTitles(listOfNotNull(alternativeTitle, this@MangaDto.title) - shownTitle, "Título alternativo")
            .build()
    }

    fun toSChapterList(): List<SChapter> = chapters.orEmpty().map { it.toSChapter(slug, id) }

    private val slug get() = Normalizer.normalize(title, Normalizer.Form.NFD)
        .replace(MARKS_REGEX, "")
        .lowercase()
        .replace(NON_ALPHA_REGEX, "-")
        .trim('-')

    companion object {
        private val MARKS_REGEX = """\p{InCombiningDiacriticalMarks}+""".toRegex()
        private val NON_ALPHA_REGEX = """[^a-z0-9]+""".toRegex()
    }
}

@Serializable
class ChapterReferenceDto(
    val mangaId: String,
    val chapterId: String,
)

@Serializable
class ReaderAccessRequestDto(
    private val mangaId: String,
    private val chapterId: String,
    private val turnstileToken: String?,
)

@Serializable
class ReaderAccessResponseDto(
    val chapter: ReaderChapterDto,
)

@Serializable
class ReaderChapterDto(
    val pages: List<String>,
)

@Serializable
class ReaderAccessErrorDto(
    val error: String,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val number: String,
    private val timestamp: Long,
) {
    fun toSChapter(
        slug: String,
        mangaId: String,
    ) = SChapter.create().apply {
        name = "Capítulo $number"
        number.toFloatOrNull()?.let { chapter_number = it }
        date_upload = timestamp
        url = "/$slug/$number"
        memo = ChapterReferenceDto(mangaId, id).toJsonElement().obj
    }
}
