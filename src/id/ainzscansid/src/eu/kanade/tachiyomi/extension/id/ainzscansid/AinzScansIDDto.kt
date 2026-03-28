package eu.kanade.tachiyomi.extension.id.ainzscansid

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

@Serializable
internal class SearchResponseDto(
    private val data: List<MangaDto>,
    @SerialName("total_pages") private val totalPages: Int,
) {
    fun toMangasPage(page: Int): MangasPage = MangasPage(
        data.map { it.toSManga() },
        page < totalPages,
    )
}

@Serializable
internal class MangaDto(
    private val title: String,
    private val slug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/comic/$slug"
        title = this@MangaDto.title
        thumbnail_url = posterImageUrl
    }
}

@Serializable
internal class SeriesDetailDto(
    private val title: String,
    private val slug: String,
    private val synopsis: String? = null,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
    @SerialName("comic_status") private val comicStatus: String? = null,
    @SerialName("author_name") private val authorName: String? = null,
    @SerialName("artist_name") private val artistName: String? = null,
    val units: List<ChapterDto> = emptyList(),
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/comic/$slug"
        title = this@SeriesDetailDto.title
        thumbnail_url = posterImageUrl
        author = authorName
        artist = artistName
        description = synopsis?.let { Jsoup.parse(it).text() }
        status = when (comicStatus?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
internal class ChapterDto(
    private val slug: String,
    private val number: String,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(comicSlug: String, dateFormat: SimpleDateFormat): SChapter = SChapter.create().apply {
        url = "/comic/$comicSlug/chapter/$slug"
        name = "Chapter ${number.removeSuffix(".00")}"
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
internal class ChapterDetailDto(
    private val chapter: ChapterPagesDto,
) {
    fun toPageList(): List<Page> = chapter.toPageList()
}

@Serializable
internal class ChapterPagesDto(
    private val pages: List<PageDto>,
) {
    fun toPageList(): List<Page> = pages.mapIndexed { i, page -> page.toPage(i) }
}

@Serializable
internal class PageDto(
    @SerialName("image_url") private val imageUrl: String,
) {
    fun toPage(index: Int): Page = Page(
        index,
        imageUrl = if (imageUrl.startsWith("http")) imageUrl else "https://api.ainzscans01.com$imageUrl",
    )
}
