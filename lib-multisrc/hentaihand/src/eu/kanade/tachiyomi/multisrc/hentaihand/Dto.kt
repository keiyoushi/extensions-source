@file:Suppress("PrivatePropertyName", "PropertyName")

package eu.kanade.tachiyomi.multisrc.hentaihand

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Created by ipcjs on 2025/9/23.
 */
@Serializable
class ResponseDto<T>(
    val data: T,
    val next_page_url: String?,
)

@Serializable
class LoginResponseDto(val auth: AuthDto) {
    @Serializable
    class AuthDto(val access_token: String)
}

@Serializable
class PageListResponseDto(val images: List<PageDto>) {
    fun toPageList() = images.map { Page(it.page, "", it.source_url) }

    @Serializable
    class PageDto(
        val page: Int,
        val source_url: String,
    )
}

typealias ChapterListResponseDto = List<ChapterDto>
typealias ChapterResponseDto = ChapterDto

@Serializable
class ChapterDto(
    private val slug: String,
    private val name: String?,
    private val added_at: String?,
    private val updated_at: String?,
) {
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private fun parseDate(date: String?): Long =
        if (date == null) {
            0
        } else if (date.contains("day")) {
            Calendar.getInstance().apply {
                add(Calendar.DATE, -date.filter { it.isDigit() }.toInt())
            }.timeInMillis
        } else {
            DATE_FORMAT.parse(date)?.time ?: 0
        }

    fun toSChapter(slug: String) = SChapter.create().also { chapter ->
        chapter.url = "$slug/${this.slug}"
        chapter.name = name ?: "Chapter"
        chapter.date_upload = parseDate(added_at)
    }

    fun toSChapter() = SChapter.create().also { chapter ->
        chapter.url = slug
        chapter.name = "Chapter"
        chapter.date_upload = parseDate(updated_at)
        chapter.chapter_number = 1f
    }
}

typealias MangaDetailsResponseDto = MangaDto

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val image_url: String?,
    private val artists: List<NameDto>?,
    private val authors: List<NameDto>?,
    private val tags: List<NameDto>?,
    private val relationships: List<NameDto>?,
    private val status: String?,
    private val alternative_title: String?,
    private val groups: List<NameDto>?,
    private val description: String?,
    private val pages: Int?,
    private val category: NameDto?,
    private val language: NameDto?,
    private val parodies: List<NameDto>?,
    private val characters: List<NameDto>?,
) {
    fun toSManga() = SManga.create().also { manga ->
        manga.url = slug.prependIndent("/en/comic/")
        manga.title = title
        manga.thumbnail_url = image_url
    }

    fun toSMangaDetails() = toSManga().also { manga ->
        manga.artist = artists?.toNames()
        manga.author = authors?.toNames() ?: manga.artist
        manga.genre = listOfNotNull(tags, relationships).flatten().toNames()
        manga.status = when (status) {
            "complete" -> SManga.COMPLETED
            "ongoing" -> SManga.ONGOING
            "onhold" -> SManga.ONGOING
            "canceled" -> SManga.COMPLETED
            else -> SManga.COMPLETED
        }
        manga.description = listOf(
            Pair("Alternative Title", alternative_title),
            Pair("Groups", groups?.toNames()),
            Pair("Description", description),
            Pair("Pages", pages?.toString()),
            Pair("Category", category?.name),
            Pair("Language", language?.name),
            Pair("Parodies", parodies?.toNames()),
            Pair("Characters", characters?.toNames()),
        ).filter { !it.second.isNullOrEmpty() }.joinToString("\n\n") { "${it.first}: ${it.second}" }
    }
}

@Serializable
class NameDto(val name: String)

fun List<NameDto>.toNames() = if (this.isEmpty()) null else this.joinToString { it.name }

@Serializable
class IdDto(val id: String)
