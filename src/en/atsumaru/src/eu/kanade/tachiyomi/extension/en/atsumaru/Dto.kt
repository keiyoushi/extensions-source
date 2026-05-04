package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class BrowseMangaDto(
    val items: List<MangaDto>,
)

@Serializable
class MangaObjectDto(
    val mangaPage: MangaDto,
)

@Serializable
class SearchResultsDto(
    val page: Int,
    val found: Int,
    val hits: List<SearchMangaDto>,
    @SerialName("request_params") val requestParams: RequestParamsDto,
) {
    fun hasNextPage(): Boolean = page * requestParams.perPage < found

    @Serializable
    class SearchMangaDto(
        val document: MangaDto,
    )

    @Serializable
    class RequestParamsDto(
        @SerialName("per_page") val perPage: Int,
    )
}

@Serializable
class MangaDto(
    // Common
    private val id: String,
    private val title: String,
    @JsonNames("poster", "image")
    private val imagePath: JsonElement,

    // Details
    private val authors: List<AuthorDto>? = null,
    private val synopsis: String? = null,
    private val genres: List<TagDto>? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val avgRating: Double? = null,
    private val mbRating: Double? = null,
    private val otherNames: List<String>? = null,
    val scanlators: List<ScanlatorDto>? = null,

    // Chapters
    val chapters: List<ChapterDto>? = null,

    val recommendations: List<MangaDto>? = null,
) {
    private fun getImagePath(): String? {
        val url = when (imagePath) {
            is JsonPrimitive -> imagePath.content
            is JsonObject -> imagePath["image"]?.jsonPrimitive?.content
            else -> null
        }
        return url?.removePrefix("/")?.removePrefix("static/")
    }

    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = id
        title = this@MangaDto.title
        thumbnail_url = getImagePath()?.let {
            val url = when {
                it.startsWith("http") -> it
                it.startsWith("//") -> "https:$it"
                else -> "$baseUrl/static/$it"
            }
            url.replaceFirst(Regex("^https?:?//"), "https://")
        }

        val ratingValue = mbRating ?: avgRating
        description = buildString {
            ratingValue?.let {
                append("Rating: %.2f/10\n\n".format(it))
            }

            synopsis?.let {
                append(it.trim())
                append("\n\n")
            }

            if (!otherNames.isNullOrEmpty()) {
                append("Alternative Names: ")
                append(otherNames.joinToString())
            }
        }.trim()

        genre = buildList {
            type?.let { add(it) }
            genres?.forEach { add(it.name) }
        }.joinToString()

        authors?.let { list ->
            author = list.filter { it.type == null || it.type == "Author" }
                .joinToString { it.name }
            artist = list.filter { it.type == "Artist" }
                .joinToString { it.name }
        }

        this@MangaDto.status?.let {
            status = when (it.lowercase().trim()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    fun recommendations(baseUrl: String) = recommendations?.map { it.toSManga(baseUrl) } ?: emptyList()

    @Serializable
    class TagDto(
        val name: String,
    )

    @Serializable
    class AuthorDto(
        val name: String,
        val type: String? = null,
    )

    @Serializable
    class ScanlatorDto(
        val id: String,
        val name: String,
    )
}

@Serializable
class AllChaptersDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: String,
    private val number: Float,
    private val title: String,
    val scanlationMangaId: String? = null,
    @SerialName("createdAt") private val date: JsonElement? = null,
) {
    fun toSChapter(slug: String, scanlatorName: String? = null): SChapter = SChapter.create().apply {
        url = "$slug/$id"
        chapter_number = number
        name = title
        scanlator = scanlatorName
        date?.let {
            date_upload = parseDate(it)
        }
    }

    private fun parseDate(dateElement: JsonElement): Long = when (dateElement) {
        is JsonPrimitive -> {
            dateElement.longOrNull ?: DATE_FORMAT.tryParse(dateElement.content.replace("T ", "T"))
        }

        else -> 0L
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}

@Serializable
class PageObjectDto(
    val readChapter: PageDto,
)

@Serializable
class PageDto(
    val pages: List<PageDataDto>,
)

@Serializable
class PageDataDto(
    val image: String,
)
