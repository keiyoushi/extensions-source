package eu.kanade.tachiyomi.extension.en.inkora

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    private val results: List<MangaDto>,
    private val hasNextPage: Boolean,
) {
    fun toMangasPage() = MangasPage(results.map { it.toSManga() }, hasNextPage)
}

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val image: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@MangaDto.title
        thumbnail_url = image
    }
}

@Serializable
class GenreListDto(val genres: List<GenreDto>)

@Serializable
class GenreDto(val name: String, val slug: String)

@Serializable
class InfoDto(
    private val id: String,
    private val title: String,
    private val altTitles: List<String> = emptyList(),
    private val image: String? = null,
    private val description: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val type: String? = null,
    private val rating: Double? = null,
    private val ratingCount: Int? = null,
    private val year: Int? = null,
    private val authors: List<String> = emptyList(),
    private val artists: List<String> = emptyList(),
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@InfoDto.title
        thumbnail_url = image
        author = authors.joinToString().ifEmpty { null }
        artist = artists.joinToString().ifEmpty { null }
        genre = (listOfNotNull(type) + genres.map { it.replace('_', ' ').replaceFirstChar(Char::uppercase) })
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when (this@InfoDto.status) {
            "releasing", "ongoing" -> SManga.ONGOING
            "finished", "completed" -> SManga.COMPLETED
            "on_hiatus", "hiatus" -> SManga.ON_HIATUS
            "discontinued", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(rating, votes = ratingCount)
            .info(year?.toString())
            .description(this@InfoDto.description)
            .altTitles(altTitles.filter { it != this@InfoDto.title })
            .build()
    }

    // Each chapter comes from one of the sources the site reads, with the same chapter from the others as versions
    fun toSChapters() = chapters
        .flatMap { listOf(it) + it.versions }
        .distinctBy { it.id }
        .map { it.toSChapter(id) }
}

@Serializable
class ChapterDto(
    val id: String,
    private val title: String? = null,
    private val chapterNumber: Float? = null,
    private val releaseDate: String? = null,
    private val scanGroup: ScanGroupDto? = null,
    val versions: List<ChapterDto> = emptyList(),
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "$mangaId/$id"
        name = title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapterNumber.toString().removeSuffix(".0")}"
        chapter_number = chapterNumber ?: -1f
        date_upload = Instant.tryParse(releaseDate)
        scanlator = scanGroup?.name
    }
}

@Serializable
class ScanGroupDto(val name: String)

@Serializable
class PageListDto(private val pages: List<PageDto>) {
    fun toPages() = pages.sortedBy { it.page }.mapIndexed { index, page -> Page(index, imageUrl = page.img) }
}

@Serializable
class PageDto(val page: Int, val img: String)
