package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
    private val largeImage: String?,

    // Details
    private val authors: JsonElement? = null,
    private val synopsis: String? = null,
    @JsonNames("genres", "tags")
    private val genres: JsonElement? = null,
    private val released: Long? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val views: JsonElement? = null,
    private val otherNames: List<String>? = null,
    private val avgRating: Float? = null,
    val scanlators: List<ScanlatorDto>? = null,

    // Chapters
    val chapters: List<ChapterDto>? = null,

    val recommendations: List<MangaDto>? = null,
) {
    private fun getImagePath(): String? {
        val url = largeImage ?: when (imagePath) {
            is JsonPrimitive -> imagePath.content
            is JsonObject -> (imagePath["largeImage"] ?: imagePath["image"])?.jsonPrimitive?.content
            else -> null
        }
        return url?.removePrefix("/")?.removePrefix("static/")
    }

    private fun parseNames(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.content
                is JsonObject -> item["name"]?.jsonPrimitive?.content
                else -> null
            }
        }
        else -> emptyList()
    }

    private fun parseAuthorsWithType(element: JsonElement?): List<Pair<String, String?>> = when (element) {
        is JsonArray -> element.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> Pair(item.content, null)
                is JsonObject -> {
                    val name = item["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val type = item["type"]?.jsonPrimitive?.content
                    Pair(name, type)
                }
                else -> null
            }
        }
        else -> emptyList()
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
            url.replaceFirst(PROTOCOL_REGEX, "https://")
        }

        description = buildList {
            avgRating?.takeIf { it > 0 }?.let {
                add("**Rating**: %.2f/10".format(Locale.ENGLISH, it))
            }

            released?.takeIf { it > 0 }?.let {
                val releaseYear = Instant.fromEpochMilliseconds(it).toJavaInstant()
                    .atZone(ZoneId.systemDefault()).year

                add("**Year**: $releaseYear")
            }

            // can be Int or formatted String
            views?.jsonPrimitive?.content?.let {
                add("**Views**: $it")
            }

            synopsis?.takeIf { it.isNotBlank() }?.let {
                add("\n")
                add("**Synopsis**: $it")
            }

            otherNames?.filter { it != this@MangaDto.title }
                ?.takeIf { it.isNotEmpty() }
                ?.let { names ->
                    val namesDesc = names.joinToString("\n") { "- $it" }
                    add("\n")
                    add("**Alternative Names**:\n$namesDesc")
                }
        }.joinToString("\n")

        genre = buildList {
            type?.let { add(it) }
            addAll(parseNames(genres))
        }.joinToString()

        val authorsList = parseAuthorsWithType(authors)
        author = authorsList.filter { it.second == "Author" || it.second == null }.joinToString { it.first }
        artist = authorsList.filter { it.second == "Artist" }.joinToString { it.first }

        status = when (this@MangaDto.status?.lowercase()?.trim()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    fun recommendations(baseUrl: String) = recommendations?.map { it.toSManga(baseUrl) } ?: emptyList()

    @Serializable
    class ScanlatorDto(
        val id: String,
        val name: String,
    )

    companion object {
        private val PROTOCOL_REGEX = Regex("^https?:?//")
    }
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
            date_upload = when {
                it is JsonPrimitive -> it.longOrNull ?: Instant.tryParse(it.content)
                else -> 0L
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

@Serializable
class FilterData(
    val genres: List<Filter>?,
    val tags: List<Filter>?,
    val types: List<Filter>?,
    val statuses: List<Filter>?,
) {
    fun getFilterList(excludedGenreIds: Set<String> = emptySet()) = buildList {
        genres?.let { add(GenreFilter(it.map { Genre(it.name, it.id) }, excludedGenreIds)) }
        tags?.let { add(TagFilters(it.map { Tag(it.name, it.id) })) }
        types?.let { add(TypeFilter(it.map { Type(it.name, it.id) })) }
        statuses?.let { add(StatusFilter(it.map { Status(it.name, it.id) })) }
    }
}

@Serializable
class Filter(
    val id: String,
    val name: String,
)
