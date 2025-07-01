package eu.kanade.tachiyomi.extension.pt.readmangas

import eu.kanade.tachiyomi.extension.pt.readmangas.LerToons.Companion.CDN_URL
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
    @JsonNames("comic_infos", "chapter")
    val content: T,
)

@Serializable
class MangaDetailsDto(
    private val props: Props<MangaDto>,
) {
    fun toSManga() = props.content.toSManga()
    fun toSChapterList() = props.content.chapters.map { it.toSChapter() }
}

@Serializable
class PageDto(
    val props: Props<ChapterWrapper>,
) {
    fun toPageList(): List<Page> {
        return props.content.chapter.pages
            .filter { it.pathSegment.contains("xml").not() }
            .mapIndexed { index, path ->
                Page(index, imageUrl = "$CDN_URL/images/${path.pathSegment}")
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
    val description: String,
    @SerialName("cover")
    val thumbnailUrl: String?,
    val slug: String,
    val status: List<ValueDto> = emptyList(),
    val genres: List<ValueDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {

    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        description = Jsoup.parseBodyFragment(this@MangaDto.description).text()
        this.thumbnail_url = thumbnailUrl?.let { "$CDN_URL/images/$it" }
        status = when (this@MangaDto.status.firstOrNull()?.name?.lowercase()) {
            "em andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString { it.name }
        url = "/comic/$slug"
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
        url = "/chapter/$path"
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
