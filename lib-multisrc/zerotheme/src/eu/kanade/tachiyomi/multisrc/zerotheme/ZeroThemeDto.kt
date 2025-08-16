package eu.kanade.tachiyomi.multisrc.zerotheme

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class Props<T>(
    @JsonNames("comic_infos", "chapter", "new_chapters")
    val content: T,
)

@Serializable
class LatestDto(
    private val props: Props<List<Comic>>,
) {
    fun toSMangaList(srcPath: String) = props.content.map { it.comic.toSManga(srcPath) }

    @Serializable
    class Comic(
        val comic: MangaDto,
    )
}

@Serializable
class MangaDetailsDto(
    private val props: Props<MangaDto>,
) {
    fun toSManga(srcPath: String) = props.content.toSManga(srcPath)
    fun toSChapterList() = props.content.chapters!!.map { it.toSChapter() }
}

@Serializable
class PageDto(
    val props: Props<ChapterWrapper>,
) {
    fun toPageList(srcPath: String): List<Page> {
        return props.content.chapter.pages
            .filter { it.pathSegment.contains("xml").not() }
            .mapIndexed { index, path ->
                Page(index, imageUrl = "$srcPath/${path.pathSegment}")
            }
    }

    @Serializable
    class ChapterWrapper(
        val chapter: Chapter,
    )

    @Serializable
    class Chapter(
        val pages: List<Image>,
    )

    @Serializable
    class Image(
        @SerialName("page_path")
        val pathSegment: String,
    )
}

@Serializable
class SearchDto(
    @SerialName("comics")
    private val page: PageDto,
) {

    val mangas: List<MangaDto> get() = page.data

    fun hasNextPage() = page.currentPage < page.lastPage

    @Serializable
    class PageDto(
        val `data`: List<MangaDto>,
        @SerialName("last_page")
        val lastPage: Int = 0,
        @SerialName("current_page")
        val currentPage: Int = 0,
    )
}

@Serializable
class MangaDto(
    val title: String,
    val description: String?,
    @SerialName("cover")
    val thumbnailUrl: String?,
    val slug: String,
    val status: List<ValueDto>? = emptyList(),
    val genres: List<ValueDto>? = emptyList(),
    val chapters: List<ChapterDto>? = emptyList(),
) {

    fun toSManga(srcPath: String) = SManga.create().apply {
        title = this@MangaDto.title
        description = this@MangaDto.description?.let { Jsoup.parseBodyFragment(it).text() }
        this.thumbnail_url = thumbnailUrl?.let { "$srcPath/$it" }

        status = when (this@MangaDto.status?.firstOrNull()?.name?.lowercase()) {
            "em andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        genre = genres?.joinToString { it.name }
        url = slug
    }

    @Serializable
    class ValueDto(
        val name: String,
    )
}

@Serializable
class ChapterDto(
    @SerialName("chapter_number")
    val number: Float,
    @SerialName("chapter_path")
    val path: String,
    @SerialName("created_at")
    val createdAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
        name = number.toString()
        chapter_number = number
        date_upload = dateFormat.tryParse(createdAt)
        url = path
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
