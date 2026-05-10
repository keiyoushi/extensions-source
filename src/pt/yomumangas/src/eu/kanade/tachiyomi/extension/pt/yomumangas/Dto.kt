package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

internal fun String.replaceB2Uri(): String = replace("b2://", "https://b2.yomumangas.com/")

@Serializable
class SearchResponse(
    val mangas: List<SearchMangaDto> = emptyList(),
    val pages: Int = 1,
)

@Serializable
class SearchMangaDto(
    private val id: Int,
    private val slug: String,
    private val title: String,
    private val cover: String,
) {
    fun toSManga() = SManga.create().apply {
        url = "$id#$slug"
        title = this@SearchMangaDto.title
        thumbnail_url = cover.replaceB2Uri()
    }
}

@Serializable
class MangaDetailsResponse(
    val manga: MangaDto,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val slug: String,
    private val title: String,
    private val cover: String,
    private val status: String? = null,
    private val description: String? = null,
    private val authors: List<String> = emptyList(),
    private val artists: List<String> = emptyList(),
    private val genres: List<TagDto> = emptyList(),
    private val tags: List<TagDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "$id#$slug"
        title = this@MangaDto.title
        thumbnail_url = cover.replaceB2Uri()
        author = authors.joinToString()
        artist = artists.joinToString()
        description = this@MangaDto.description
        genre = (genres + tags).map { it.name }.distinct().joinToString()
        status = when (this@MangaDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETE" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class ChaptersResponse(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter") private val chapterNumber: String,
    private val title: String? = null,
    @SerialName("uploaded_at") private val uploadedAt: String? = null,
) {
    fun toSChapter(mangaId: String, mangaSlug: String, dateFormat: SimpleDateFormat): SChapter = SChapter.create().apply {
        url = "/mangas/$mangaId/$mangaSlug/$chapterNumber"
        val parsedTitle = title?.trim()?.removePrefix("-")?.trim()
        name = if (!parsedTitle.isNullOrEmpty()) {
            "Capítulo $chapterNumber - $parsedTitle"
        } else {
            "Capítulo $chapterNumber"
        }
        date_upload = dateFormat.tryParse(uploadedAt)
    }
}
