package eu.kanade.tachiyomi.extension.en.mangade

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class PayloadDto<T>(
    val data: T,
)

@Serializable
class MangaListPageDto(
    private val list: List<MangaDto>,
    private val totalPage: Int,
    private val page: String,
) {
    fun toMangasPage(): MangasPage {
        val mangas = list.map { it.toSManga() }
        return MangasPage(mangas, page.toInt() < totalPage)
    }
}

@Serializable
class MangaDto(
    private val id: String,
    private val name: String,
    private val slug: String? = null,
    private val image: String,
    private val description: String? = null,
    @SerialName("genre_names") private val genreNames: String? = null,
    private val status: String? = null,
    @SerialName("news_chapters") private val newsChapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = image
        url = "/$slug?mid=$id"
        description = this@MangaDto.description
        genre = genreNames?.replace(",", ", ")
        status = when (this@MangaDto.status) {
            "Ongoing", "Releasing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "On Hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapterList(dateFormat: SimpleDateFormat): List<SChapter> = newsChapters.map { it.toSChapter(id, slug, dateFormat) }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val name: String,
    private val slug: String? = null,
    @SerialName("chapter_number") private val chapterNumber: String? = null,
    @SerialName("published_date") private val publishedDate: String? = null,
    @SerialName("chapter_images") private val chapterImages: List<PageDto> = emptyList(),
) {
    fun toSChapter(mangaId: String, mangaSlug: String?, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        name = this@ChapterDto.name
        chapter_number = this@ChapterDto.chapterNumber?.toFloatOrNull() ?: -1f
        url = "/$mangaSlug/$slug?cid=$id&mid=$mangaId"
        date_upload = dateFormat.tryParse(publishedDate)
    }

    fun toPageList(): List<Page> = chapterImages.mapIndexed { index, pageDto ->
        pageDto.toPage(index)
    }
}

@Serializable
class PageDto(
    private val image: String,
) {
    fun toPage(index: Int) = Page(index, imageUrl = image)
}

@Serializable
class GenreListPageDto(
    val genres: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)
