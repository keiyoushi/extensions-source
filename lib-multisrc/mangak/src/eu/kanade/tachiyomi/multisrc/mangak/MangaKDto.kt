package eu.kanade.tachiyomi.multisrc.mangak

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SearchResponseDto(
    val data: SearchDataDto,
)

@Serializable
class SearchDataDto(
    val items: List<MangaItemDto>,
    val pagination: PaginationDto,
)

@Serializable
class MangaItemDto(
    private val id: String,
    private val name: String,
    private val cover: String? = null,
    private val url: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = cover
        url = this@MangaItemDto.url
        memo = buildJsonObject {
            put("id", id)
        }
    }
}

@Serializable
class PaginationDto(
    @SerialName("has_next") val hasNext: Boolean,
)

@Serializable
class ChapterListResponseDto(
    val data: ChapterDataDto,
)

@Serializable
class ChapterDataDto(
    val chapters: List<ChapterItemDto>,
)

@Serializable
class ChapterItemDto(
    private val url: String,
    private val name: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = this@ChapterItemDto.url
        name = this@ChapterItemDto.name
        date_upload = Instant.tryParse(updatedAt)
    }
}

@Serializable
class MangaPageDto(
    val pageProps: MangaPagePropsDto,
)

@Serializable
class MangaPagePropsDto(
    val initialManga: InitialMangaDto? = null,
)

@Serializable
class ChapterPageDto(
    val pageProps: ChapterPagePropsDto,
)

@Serializable
class ChapterPagePropsDto(
    val initialChapter: InitialChapterDto? = null,
)

@Serializable
class InitialMangaDto(
    val id: String,
    private val name: String,
    private val url: String,
    private val cover: String? = null,
    private val summary: String? = null,
    private val status: String? = null,
    private val rating: Double? = null,
    private val ratingsCount: Int? = null,
    private val releaseDate: String? = null,
    private val contentRating: String? = null,
    private val type: EntityDto? = null,
    private val authors: List<EntityDto>? = null,
    private val artists: List<EntityDto>? = null,
    private val demographics: List<EntityDto>? = null,
    private val formats: List<EntityDto>? = null,
    private val genres: List<EntityDto>? = null,
    private val tags: List<EntityDto>? = null,
    private val altNames: List<EntityDto>? = null,
    private val trackers: List<TrackerDto>? = null,
) {
    fun toSManga(mangaUrl: String = url, includeTags: Boolean = true) = SManga.create().apply {
        url = mangaUrl
        title = name
        thumbnail_url = cover
        author = authors.orEmpty().joinToString { it.name }.ifEmpty { null }
        artist = artists.orEmpty().joinToString { it.name }.ifEmpty { null }
        genre = buildList {
            type?.name?.takeIf { it != "other" }?.let { add(it.replaceFirstChar(Char::uppercase)) }
            addAll((demographics.orEmpty() + formats.orEmpty() + genres.orEmpty()).map { it.name })
            if (includeTags) {
                addAll(tags.orEmpty().map { it.name })
            }
        }.distinctBy { it.lowercase() }.joinToString().ifEmpty { null }
        status = when (this@InitialMangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(rating.takeIf { (ratingsCount ?: 0) > 0 }, max = 5, votes = ratingsCount)
            .info(releaseDate, contentRating?.replaceFirstChar(Char::uppercase))
            .markdown(summary)
            .altTitles(altNames.orEmpty().map { it.name }.filter { it != name })
            .links(trackers.orEmpty().map { (TRACKER_NAMES[it.slug] ?: it.slug) to it.url })
            .build()
        memo = buildJsonObject {
            put("id", id)
        }
    }
}

@Serializable
class EntityDto(
    val name: String,
)

@Serializable
class TrackerDto(
    val slug: String,
    val url: String,
)

@Serializable
class InitialChapterDto(
    val images: List<String>,
)

@Serializable
class GenreResponseDto(
    val data: GenreDataDto,
)

@Serializable
class GenreDataDto(
    val items: List<GenreItemDto>,
)

@Serializable
class GenreItemDto(
    val name: String,
    val slug: String,
)

private val TRACKER_NAMES = mapOf(
    "anilist" to "AniList",
    "mal" to "MyAnimeList",
    "mangadex" to "MangaDex",
    "mangaupdates" to "MangaUpdates",
    "mangabaka" to "MangaBaka",
)
