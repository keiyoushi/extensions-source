package eu.kanade.tachiyomi.extension.all.littlegarden

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val items: List<MangaItemDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class MangaItemDto(
    private val title: String,
    private val slug: String,
    private val coverUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$slug"
        title = this@MangaItemDto.title
        thumbnail_url = coverUrl?.let { baseUrl + it }
    }
}

@Serializable
class MangaDetailsDto(
    val manga: MangaDto,
    val chapters: List<ChapterDto>,
)

@Serializable
class MangaDto(
    val slug: String,
    private val title: String,
    private val altTitle: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    private val coverUrl: String? = null,
    private val coverFullUrl: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/$slug"
        title = this@MangaDto.title
        thumbnail_url = coverFullUrl ?: coverUrl?.let { baseUrl + it }
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        genre = (genres + tags).distinct().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(this@MangaDto.description)
            .altTitles(altTitle?.split(", ").orEmpty())
            .build()
    }
}

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String? = null,
    private val slug: String,
    private val publishedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/$mangaSlug/$slug"
        val defaultName = "Chapitre ${number.toString().removeSuffix(".0")}"
        name = title?.takeIf { it != defaultName }?.let { "$defaultName - $it" } ?: defaultName
        chapter_number = number
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(val pages: List<PageDto>)

@Serializable
class PageDto(
    private val url: String,
    private val coloredUrl: String? = null,
) {
    val imageUrl get() = coloredUrl ?: url
}
