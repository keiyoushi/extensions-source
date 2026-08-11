package eu.kanade.tachiyomi.extension.en.duskscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    private val alternativeTitle: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val cover: String? = null,
    val status: String? = null,
    val type: String? = null,
    val views: Int = 0,
    val rating: Double = 0.0,
    val genres: List<String> = emptyList(),
    val createdAt: String? = null,
) {
    fun matchesQuery(query: String) = title.contains(query, ignoreCase = true) ||
        alternativeTitle?.contains(query, ignoreCase = true) == true
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Int,
    private val title: String = "",
    private val releaseDate: String? = null,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        url = id
        // URL is built from the slug and number, id alone can't provide
        memo = buildJsonObject {
            put("slug", slug)
            put("number", number)
        }
        name = title.ifBlank { "Chapter $number" }
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(releaseDate)
    }
}

@Serializable
class SeriesPageDto(
    @SerialName("initialManga") val manga: MangaDto,
    @SerialName("initialChapters") val chapters: List<ChapterDto>,
)

@Serializable
class FilterDataDto(
    val genres: List<String>,
    val statuses: List<String>,
    val types: List<String>,
)

@Serializable
class ChapterDetailDto(
    private val pages: String,
) {
    // The API returns the page list as a JSON-encoded string, not an array.
    val pageUrls get() = pages.parseAs<List<String>>()
}

fun MangaDto.toSManga(): SManga = SManga.create().apply {
    url = slug
    title = this@toSManga.title
    thumbnail_url = cover
    author = this@toSManga.author
    artist = this@toSManga.artist
    description = this@toSManga.description
    genre = genres.joinToString()
    status = when (this@toSManga.status) {
        "Ongoing" -> SManga.ONGOING
        "Completed" -> SManga.COMPLETED
        "Hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
    initialized = true
}
