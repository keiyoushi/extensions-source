package eu.kanade.tachiyomi.extension.vi.vietmanhwa

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable

@Serializable
class ListDto(
    private val manga: List<MangaDto>,
    private val currentPage: Int,
    private val totalPages: Int,
) {
    fun toMangasPage() = MangasPage(manga.map { it.toSManga() }, currentPage < totalPages)
}

@Serializable
class AuthorDto(
    private val mangas: List<MangaDto>,
) {
    fun toMangasPage() = MangasPage(mangas.map { it.toSManga() }, false)
}

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val poster: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = poster
    }
}

@Serializable
class DetailsDto(
    private val manga: MangaDetailsDto,
    private val chapters: List<ChapterDto>,
    private val genreDisplayMap: Map<String, String>,
) {
    fun toSManga(slug: String) = manga.toSManga(slug, genreDisplayMap)

    fun toSChapterList(slug: String) = chapters.map { it.toSChapter(slug) }
}

@Serializable
class MangaDetailsDto(
    private val title: String,
    private val alternateTitle: String? = null,
    private val description: String? = null,
    private val poster: String? = null,
    private val genres: List<String> = emptyList(),
    private val authorNames: List<String> = emptyList(),
    private val translatorNames: List<String> = emptyList(),
    private val doujinshiNames: List<String> = emptyList(),
    private val characterNames: List<String> = emptyList(),
    private val userStatus: Int? = null,
) {
    fun toSManga(slug: String, genreNames: Map<String, String>) = SManga.create().apply {
        url = slug
        title = this@MangaDetailsDto.title
        thumbnail_url = poster
        author = authorNames.joinToString().ifEmpty { null }
        genre = genres.map { genreNames[it] ?: it }.joinToString().ifEmpty { null }
        status = when (userStatus) {
            0 -> SManga.ONGOING
            1 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@MangaDetailsDto.description)
            .field("Doujinshi", doujinshiNames)
            .field("Nhân vật", characterNames)
            .field("Dịch giả", translatorNames)
            // Several names are joined with "|", and some just repeat the title
            .altTitles(alternateTitle?.split('|').orEmpty().filter { it.trim() != this@MangaDetailsDto.title }, "Tên khác")
            .build()
    }
}

@Serializable
class ChapterDto(
    private val title: String,
    private val slug: String,
    private val publishedAt: Long? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = title
        date_upload = publishedAt ?: 0L
    }
}

@Serializable
class ReaderDto(
    private val chapter: ReaderChapterDto,
) {
    fun toPageList() = chapter.toPageList()
}

@Serializable
class ReaderChapterDto(
    private val contentUrls: List<String>,
) {
    fun toPageList() = contentUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
}

@Serializable
class GenresDto(
    val genres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val slug: String,
    val name: String,
)
