package eu.kanade.tachiyomi.extension.tr.mangadiyari

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class SeriesListResponse(
    val series: List<SeriesDto>,
    val hasMore: Boolean,
)

@Serializable
class LatestUpdatesResponse(
    val updates: List<SeriesDto>,
    val hasMore: Boolean,
)

@Serializable
class SeriesDetailsResponse(
    val series: SeriesDto,
    val chapters: List<ChapterDto>,
)

@Serializable
class SeriesDto(
    val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<String> = emptyList(),
    @SerialName("alt_names") private val altNames: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverUrl?.let { baseUrl.resolveImage(it) }
        author = this@SeriesDto.author
        artist = this@SeriesDto.artist
        genre = buildList {
            type?.let { add(it.replaceFirstChar(Char::uppercase)) }
            addAll(genres)
        }.joinToString().ifBlank { null }
        description = buildString {
            this@SeriesDto.description?.let {
                append(Jsoup.parseBodyFragment(it).wholeText().replace(PARAGRAPH_GAP, "\n\n").trim())
            }
            if (!altNames.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif isimler: ")
                append(altNames)
            }
        }.ifBlank { null }
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_number") val chapterNumber: Float,
    private val title: String? = null,
    @SerialName("publish_at") private val publishAt: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")

        url = id.toString()
        memo = buildJsonObject {
            put("slug", slug)
            put("number", number)
        }
        name = title?.takeIf { it.isNotBlank() } ?: "Bölüm $number"
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(publishAt ?: createdAt)
    }
}

@Serializable
class ChapterPagesResponse(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    @SerialName("page_number") val pageNumber: Int,
    @SerialName("display_image") val displayImage: String,
)

private val PARAGRAPH_GAP = Regex("""\s*\n\s*\n\s*""")

fun String.resolveImage(src: String): String = when {
    src.startsWith("http") -> src
    src.startsWith("/") -> this + src
    else -> "$this/$src"
}
