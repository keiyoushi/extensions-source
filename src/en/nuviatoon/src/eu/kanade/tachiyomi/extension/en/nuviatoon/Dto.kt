package eu.kanade.tachiyomi.extension.en.nuviatoon

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.time.Instant

@Serializable
internal class ComicsResponse(
    private val data: List<ComicDto>,
    private val meta: PageMeta,
) {
    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, meta.hasNextPage)
}

@Serializable
internal class PageMeta(
    private val page: Int,
    private val totalPages: Int,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
internal class DetailResponse(val data: ComicDto)

@Serializable
internal class ComicDto(
    private val slug: String,
    private val title: String,
    private val coverThumbUrl: String? = null,
    private val coverUrl: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val seriesStatus: String? = null,
    private val genres: List<GenreDto>? = null,
    private val chapters: List<ChapterDto>? = null,
    private val altTitles: List<String>? = null,
    private val ratingAvg: Float? = null,
    private val ratingCount: Int = 0,
) {
    fun toSManga() = SManga.create().apply {
        title = this@ComicDto.title
        url = slug
        thumbnail_url = coverUrl ?: coverThumbUrl
        author = this@ComicDto.author
        artist = this@ComicDto.artist
        description = buildString {
            val avg = ratingAvg
            if (ratingCount > 0 && avg != null) {
                append("Rating: ")
                append("%.1f".format(Locale.US, avg).removeSuffix(".0"))
                append("/5 (")
                append(ratingCount)
                append(" ratings)\n\n")
            }
            this@ComicDto.description?.let { append(it) }
            if (!altTitles.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Associated Name(s):\n")
                altTitles.forEach { append("- $it\n") }
            }
        }.trim().ifEmpty { null }
        genre = genres?.joinToString { it.name }
        status = parseStatus(seriesStatus)
    }

    fun toSChapterList(hideLocked: Boolean) = chapters.orEmpty().filterNot { hideLocked && it.isLocked }.map { it.toSChapter(slug) }

    private fun parseStatus(status: String?) = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
internal class GenreDto(val name: String)

@Serializable
internal class ChapterDto(
    private val id: String,
    private val title: String? = null,
    private val number: Float? = null,
    private val publishedAt: String? = null,
    private val priceCoins: Int = 0,
) {
    val isLocked get() = priceCoins > 0

    fun toSChapter(slug: String) = SChapter.create().apply {
        val numberString = number?.toString()?.removeSuffix(".0") ?: ""
        val base = title?.takeIf { it.isNotBlank() } ?: "Chapter $numberString".trim()
        name = if (isLocked) "🔒 $base" else base
        url = "$slug/chapter/$numberString"
        memo = buildJsonObject { put("chapterId", id) }
        chapter_number = number ?: -1f
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
internal class ContentResponse(val data: ContentDto)

@Serializable
internal class ContentDto(private val pages: List<PageDto>) {
    fun toPageList() = pages.sortedBy { it.index }.mapIndexed { index, dto -> Page(index, imageUrl = dto.url) }
}

@Serializable
internal class PageDto(val index: Int, val url: String)
