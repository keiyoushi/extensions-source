package eu.kanade.tachiyomi.extension.en.mangapdf

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    private val items: List<MangaSummaryDto>,
    @SerialName("has_next")
    private val hasNext: Boolean,
) {
    fun toMangasPage(): MangasPage = MangasPage(
        mangas = items.map { it.toSManga() },
        hasNextPage = hasNext,
    )
}

@Serializable
class MangaSummaryDto(
    private val id: String,
    private val title: String,
    @SerialName("thumbnail_url")
    private val thumbnailUrl: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        this.title = this@MangaSummaryDto.title
        thumbnail_url = thumbnailUrl
    }
}

@Serializable
class MangaUpdateResponse(
    private val manga: MangaDto,
    private val chapters: List<ChapterDto>,
) {
    fun toSMangaUpdate(): SMangaUpdate = SMangaUpdate(
        manga = manga.toSManga(),
        chapters = chapters.map { it.toSChapter() },
    )
}

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    @SerialName("thumbnail_url")
    private val thumbnailUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String = "unknown",
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        this.title = this@MangaDto.title
        thumbnail_url = thumbnailUrl
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        description = this@MangaDto.description
        genre = genres.takeIf { it.isNotEmpty() }?.joinToString()
        this.status = this@MangaDto.status.toMihonStatus()
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    private val name: String,
    private val number: Float? = null,
    private val scanlator: String? = null,
    @SerialName("uploaded_at")
    private val uploadedAt: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        this.name = this@ChapterDto.name
        chapter_number = number ?: -1f
        this.scanlator = this@ChapterDto.scanlator
        date_upload = Instant.tryParse(uploadedAt)
    }
}

@Serializable
class PageListResponse(
    private val pages: List<PageDto>,
) {
    fun toPages(): List<Page> = pages.mapIndexed { index, page ->
        page.toPage(index)
    }
}

@Serializable
class PageDto(
    @SerialName("image_url")
    private val imageUrl: String,
) {
    fun toPage(listIndex: Int): Page = Page(
        index = listIndex,
        imageUrl = imageUrl,
    )
}

private fun String.toMihonStatus(): Int = when (lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "licensed" -> SManga.LICENSED
    "publishing_finished" -> SManga.PUBLISHING_FINISHED
    "cancelled" -> SManga.CANCELLED
    "on_hiatus" -> SManga.ON_HIATUS
    else -> SManga.UNKNOWN
}
