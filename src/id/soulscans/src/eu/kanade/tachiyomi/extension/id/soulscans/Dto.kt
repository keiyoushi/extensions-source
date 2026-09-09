package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListResponseDto(
    val data: List<MangaListItemDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaListItemDto(
    private val title: String,
    private val slug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaListItemDto.title
        url = "/comic/$slug"
        thumbnail_url = posterImageUrl
    }
}

@Serializable
class GenreDto(
    private val slug: String,
    private val name: String,
) {
    fun toPair() = name to slug

    override fun toString() = name
}

@Serializable
class SeriesDetailDto(
    private val title: String,
    private val slug: String,
    private val synopsis: String?,
    @SerialName("poster_image_url") private val posterImageUrl: String?,
    @SerialName("author_name") private val authorName: String?,
    @SerialName("artist_name") private val artistName: String?,
    @SerialName("comic_status") private val comicStatus: String?,
    private val genres: List<GenreDto> = emptyList(),
    private val units: List<UnitDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@SeriesDetailDto.title
        url = "/comic/$slug"
        description = synopsis
        thumbnail_url = posterImageUrl
        author = authorName
        artist = artistName
        genre = genres.joinToString()
        status = comicStatus.parseStatus()
    }

    fun toSChapterList() = units.map { it.toSChapter(slug) }

    private fun String?.parseStatus() = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled", "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class UnitDto(
    private val slug: String,
    private val number: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        url = "/comic/$seriesSlug/chapter/$slug"
        name = "Chapter " + number.toFloatOrNull()?.toString()?.removeSuffix(".0")
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesResponseDto(private val chapter: ChapterPagesDto) {
    fun toPageList() = chapter.toPageList()
}

@Serializable
class ChapterPagesDto(private val pages: List<PageDto>) {
    fun toPageList() = pages.mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
}

@Serializable
class PageDto(@SerialName("image_url") val imageUrl: String)
