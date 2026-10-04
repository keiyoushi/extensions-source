package eu.kanade.tachiyomi.extension.vi.damconuong

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.time.Instant

@Serializable
class PagesResponse(
    @SerialName("e") val encrypted: String,
)

@Serializable
class PagesPayload(
    @SerialName("p") val pages: List<String> = emptyList(),
    @SerialName("s") val scrambleKeys: List<String?>? = null,
)

@Serializable
class ListResponse(
    val data: List<MangaDto> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class DetailResponse(
    val data: MangaDto,
)

@Serializable
class GenreListResponse(
    val data: List<GenreOption> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class ChapterListResponse(
    val data: List<ChapterDto> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
)

@Serializable
class MangaDto(
    val name: String,
    val slug: String,
    val pilot: String? = null,
    val status: Int? = null,
    @SerialName("cover_full_url") val coverFullUrl: String? = null,
    val artist: PersonDto? = null,
    val author: PersonDto? = null,
    val group: PersonDto? = null,
    val genres: List<GenreOption> = emptyList(),
    @SerialName("requires_login") val requiresLogin: Boolean = false,
) {
    fun toSManga() = SManga.create().apply {
        url = "/truyen/$slug"
        title = name
        thumbnail_url = coverFullUrl
    }

    fun toSMangaDetails() = toSManga().apply {
        description = pilot?.let { htmlToText(it) }?.ifEmpty { null }
        author = this@MangaDto.author?.name ?: this@MangaDto.artist?.name
        genre = genres.joinToString { it.name }.ifEmpty { null }
        status = when (this@MangaDto.status) {
            2 -> SManga.ONGOING
            1 -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class PersonDto(
    val name: String,
    val slug: String? = null,
)

@Serializable
class GenreOption(
    val id: Int,
    val name: String,
    val slug: String? = null,
)

@Serializable
class ChapterDto(
    val name: String,
    val slug: String,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/truyen/$mangaSlug/$slug"
        name = this@ChapterDto.name
        date_upload = Instant.tryParse(createdAt)
    }
}

private fun htmlToText(html: String): String {
    val text = Jsoup.parse(html).wholeOwnText()
    return Parser.unescapeEntities(text, false).trim()
}
