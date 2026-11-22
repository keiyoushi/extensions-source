package eu.kanade.tachiyomi.extension.id.hatsu

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class ResponseDto<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
class SearchDto(
    val mangas: List<MangaItemDto>,
    val total: Int,
)

@Serializable
class ProjectDto(
    val project: List<MangaItemDto>,
    val total: Int,
)

@Serializable
class MangaItemDto(
    private val title: String,
    private val slug: String,
    private val featuredImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaItemDto.title
        thumbnail_url = featuredImage
    }
}

@Serializable
class MangaDetailDto(
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val featuredImage: String? = null,
    private val metadata: MetadataDto? = null,
    val chapters: ChapterListDto,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDetailDto.title
        thumbnail_url = featuredImage
        author = metadata?.author?.joinToString { it.name }
        artist = metadata?.artist?.joinToString { it.name }
        genre = metadata?.genre?.joinToString { it.name }
        status = when (this@MangaDetailDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(metadata?.score?.takeIf { it > 0 })
            .info(type?.lowercase()?.replaceFirstChar { it.uppercase() })
            .separator()
            .description(this@MangaDetailDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .altTitles(metadata?.alternateTitles.orEmpty().filterNot { it.equals(title, ignoreCase = true) })
            .build()
    }
}

@Serializable
class MetadataDto(
    val alternateTitles: List<String> = emptyList(),
    val genre: List<NamedDto> = emptyList(),
    val author: List<NamedDto> = emptyList(),
    val artist: List<NamedDto> = emptyList(),
    val score: Double? = null,
)

@Serializable
class NamedDto(
    val name: String,
)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val createdAt: String? = null,
) {
    // the site's chapter titles repeat the manga title in mixed casing, so the name is built from the number
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val chapterNumber = number.toString().removeSuffix(".0")
        url = "/manga/$mangaSlug/chapter-$chapterNumber"
        name = "Chapter $chapterNumber"
        chapter_number = number
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesDto(
    private val medias: List<MediaDto>,
) {
    fun toPageList() = medias.sortedBy { it.pageNumber }.mapIndexed { index, media ->
        Page(index, imageUrl = media.filePath)
    }
}

@Serializable
class MediaDto(
    val filePath: String,
    val pageNumber: Int,
)

@Serializable
class GenreListDto(
    val allGenres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)
