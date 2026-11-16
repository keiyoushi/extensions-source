package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Serializable
class BrowseMangaDto(
    val items: List<MangaDto>,
)

@Serializable
class MangaObjectDto(
    val mangaPage: MangaPageDto,
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
    val id: String,
    val title: String,
    // Browse and related entries carry largeImage, search documents only posterMedium
    private val largeImage: String? = null,
    private val posterMedium: String? = null,
    val medium: String? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = id
        title = this@MangaDto.title
        thumbnail_url = (largeImage ?: posterMedium?.replace("-medium.", "-large."))?.let { cdnUrl(baseUrl, it) }
    }
}

@Serializable
class MangaPageDto(
    private val id: String,
    private val title: String,
    private val poster: PosterDto,
    private val authors: List<AuthorDto>,
    private val synopsis: String? = null,
    private val genres: List<NameDto>,
    private val tags: List<NameDto>,
    private val released: Long? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val views: String? = null,
    private val otherNames: List<String>,
    private val avgRating: Float? = null,
    val scanlators: List<ScanlatorDto>,
    private val anilistId: String? = null,
    private val malId: String? = null,
    private val mangaUpdatesId: String? = null,
    private val mangaBakaId: String? = null,
    private val kitsuId: String? = null,
    private val annId: String? = null,
    private val apId: String? = null,
    private val kenmeiUrl: String? = null,
    private val relations: List<RelationDto>? = null,
    private val recommendations: List<MangaDto>? = null,
    private val similarManga: List<MangaDto>? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = id
        title = this@MangaPageDto.title
        thumbnail_url = cdnUrl(baseUrl, poster.largeImage)
        author = authors.filter { it.type != "Artist" }.joinToString { it.name }.ifEmpty { null }
        artist = authors.filter { it.type == "Artist" }.joinToString { it.name }.ifEmpty { null }
        genre = buildList {
            // The site's data spells Manhwa "Manwha"
            type?.let { add(if (it == "Manwha") "Manhwa" else it) }
            genres.mapTo(this) { it.name }
            tags.mapTo(this) { it.name }
        }.distinct().joinToString()
        status = when (this@MangaPageDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(avgRating?.takeIf { it > 0 })
            .field("Year", released?.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it).toJavaInstant().atZone(ZoneOffset.UTC).year.toString() })
            .field("Views", views)
            .separator()
            .markdown(synopsis)
            .links(
                listOf(
                    "AniList" to anilistId?.let { "https://anilist.co/manga/$it" },
                    "MyAnimeList" to malId?.let { "https://myanimelist.net/manga/$it" },
                    "MangaUpdates" to mangaUpdatesId?.let { "https://www.mangaupdates.com/series/$it" },
                    "MangaBaka" to mangaBakaId?.let { "https://mangabaka.org/$it" },
                    "Kitsu" to kitsuId?.let { "https://kitsu.app/manga/$it" },
                    "AnimeNewsNetwork" to annId?.let { "https://www.animenewsnetwork.com/encyclopedia/manga.php?id=$it" },
                    "AnimePlanet" to apId?.let { "https://www.anime-planet.com/manga/$it" },
                    "Kenmei" to kenmeiUrl,
                ),
            )
            .links(
                relations.orEmpty().filter { it.manga.medium != "Novel" }
                    .map { "${it.typeName()}: ${it.manga.title}" to "$baseUrl/manga/${it.manga.id}" },
                label = "Related",
            )
            .altTitles(otherNames.filter { it != this@MangaPageDto.title })
            .build()
    }

    fun relatedManga(baseUrl: String): List<SManga> = (relations.orEmpty().map { it.manga } + recommendations.orEmpty() + similarManga.orEmpty())
        .filter { it.medium != "Novel" }
        .map { it.toSManga(baseUrl) }
        .distinctBy { it.url }

    @Serializable
    class PosterDto(
        val largeImage: String,
    )

    @Serializable
    class AuthorDto(
        val name: String,
        val type: String? = null,
    )

    @Serializable
    class NameDto(
        val name: String,
    )

    @Serializable
    class RelationDto(
        private val type: String,
        val manga: MangaDto,
    ) {
        fun typeName(): String = type.replace(CAMEL_CASE_REGEX, " ")
    }

    @Serializable
    class ScanlatorDto(
        val id: String,
        val name: String,
        val score: Int,
    )
}

internal fun cdnUrl(baseUrl: String, path: String): String? {
    val base = baseUrl.toHttpUrl()
    return base.newBuilder().host("cdn.${base.host}").encodedPath("/static/").build().resolve(path)?.toString()
}

private val CAMEL_CASE_REGEX = Regex("""(?<=[a-z])(?=[A-Z])""")

@Serializable
class AllChaptersDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Float,
    private val title: String,
    val scanlationMangaId: String? = null,
    @SerialName("createdAt") private val date: JsonElement? = null,
) {
    fun dateUploadMillis(): Long = when (date) {
        is JsonPrimitive -> date.longOrNull ?: Instant.tryParse(date.content)
        else -> 0L
    }

    fun toSChapter(slug: String, scanlatorName: String? = null): SChapter = SChapter.create().apply {
        url = "$slug/$id"
        chapter_number = number
        name = title
        scanlator = scanlatorName
        date?.let { date_upload = dateUploadMillis() }
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
    private val genres: List<FilterDto>?,
    private val tags: List<FilterDto>?,
    private val types: List<FilterDto>?,
    private val statuses: List<FilterDto>?,
) {
    fun getFilterList(excludedGenreIds: Set<String>) = buildList {
        genres?.let { genres ->
            add(
                GenreFilter(
                    genres.map {
                        val state = if (it.id in excludedGenreIds) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE
                        TriStateOption(it.name, it.id, state)
                    },
                ),
            )
        }
        tags?.let { tags ->
            val letters = tags.sortedBy { it.name }
                .groupBy { tag -> tag.name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "0-9" }
                .map { (letter, letterTags) -> TagLetterFilter(letter, letterTags.map { TriStateOption(it.name, it.id) }) }
            add(TagFilters(letters))
        }
        types?.let { add(TypeFilter(it.map { type -> CheckBoxOption(type.name, type.id) })) }
        statuses?.let { add(StatusFilter(it.map { status -> CheckBoxOption(status.name, status.id) })) }
    }
}

@Serializable
class FilterDto(
    val id: String,
    val name: String,
)
