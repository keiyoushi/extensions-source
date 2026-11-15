package eu.kanade.tachiyomi.extension.en.asuratoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class BrowseDto(
    val manga: List<MangaDto>,
    private val currentPage: Int,
    private val totalPages: Int,
) {
    val hasNextPage get() = currentPage < totalPages
}

@Serializable
class SearchDto(
    val manga: List<MangaDto>,
    private val page: Int,
    private val totalPages: Int,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    private val coverImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverImage
    }
}

@Serializable
class MangaPageDto(
    val manga: MangaDetailsDto,
)

@Serializable
class MangaDetailsDto(
    private val title: String,
    private val slug: String,
    private val alternativeName: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val coverImage: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val description: String? = null,
    val chapters: List<ChapterDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDetailsDto.title
        thumbnail_url = coverImage
        author = this@MangaDetailsDto.author?.ifEmpty { null }
        artist = this@MangaDetailsDto.artist?.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(this@MangaDetailsDto.description)
            .altTitles(alternativeName?.split(", ").orEmpty())
            .build()
        genre = genres.joinToString().ifEmpty { null }
        status = when (this@MangaDetailsDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    private val chapterNumber: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/chapter/$slug"
        name = title
        chapterNumber?.toFloatOrNull()?.let { chapter_number = it }
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPageDto(
    val chapter: ChapterImagesDto,
    val manga: MangaRefDto,
)

@Serializable
class ChapterImagesDto(
    val images: List<String>,
)

@Serializable
class MangaRefDto(
    val slug: String,
)
