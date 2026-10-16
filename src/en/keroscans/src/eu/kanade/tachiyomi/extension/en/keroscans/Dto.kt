package eu.kanade.tachiyomi.extension.en.keroscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
internal data class DataDto<T>(
    val data: T,
    val meta: MetaDto,
)

@Serializable
internal data class MetaDto(
    val hasMore: Boolean,
)

@Serializable
internal data class GenresTagsContainerDto(
    val genres: List<TagDto>,
    val tags: List<TagDto>,
)

@Serializable
internal data class SeriesSummaryDto(
    val id: String,
    val slug: String,
    val urlSlug: String,
    val title: String,
    val coverImage: String,
    val type: String,
    val genres: List<GenreDto>,
    val status: String? = null,
    val chapters: List<ChapterSummaryDto>? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = "/series/comic/$urlSlug"
        title = this@SeriesSummaryDto.title
        thumbnail_url = "$baseUrl$coverImage"
        genre = genres.joinToString { it.genre.slug }.ifBlank { null }
        status = when (this@SeriesSummaryDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "DROPPED" -> SManga.CANCELLED
            "DISCONTINUED" -> SManga.CANCELLED
            "UPCOMING" -> SManga.UNKNOWN
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
internal data class GenreDto(
    val genre: SlugDto,
)

@Serializable
internal data class SlugDto(
    val slug: String,
)

@Serializable
internal data class SeriesContainerDto(
    val series: SeriesDto,
    val chapters: List<ChapterSummaryDto>,
    val similarSeries: List<SeriesSummaryDto>,
)

@Serializable
internal data class SeriesDto(
    val id: String,
    val title: String,
    val slug: String,
    val urlSlug: String,
    val altTitle: String,
    val origin: String,
    val originalTitle: String,
    val aliases: List<String>,
    val description: String,
    val coverImage: String,
    val status: String,
    val updatedAt: String,
    val genres: List<TagDto>,
    val tags: List<TagDto>,
    val team: TeamDto,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        this.url = "$baseUrl/series/comic/$urlSlug"
        title = this@SeriesDto.title
        description = this@SeriesDto.description
        thumbnail_url = "$baseUrl$coverImage"
        genre = listOf(genres, tags).flatten().joinToString { it.name }.ifBlank { null }
        status = when (this@SeriesDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "DROPPED" -> SManga.CANCELLED
            "DISCONTINUED" -> SManga.CANCELLED
            "UPCOMING" -> SManga.UNKNOWN
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject {
            put("scanlator", team.name)
        }
    }
}

@Serializable
internal data class TagDto(
    val name: String,
    val slug: String,
)

@Serializable
internal data class TeamDto(
    val name: String,
)

@Serializable
internal data class ChapterSummaryDto(
    val id: String,
    val number: Float,
    val title: String,
    val coinPrice: Int,
    val publishedAt: String,
    val hasAccess: Boolean? = null,
    val unlockedAt: String? = null,
) {
    val accessible: Boolean = hasAccess ?: (unlockedAt != null)

    fun toSChapter(manga: SManga) = SChapter.create().apply {
        name = if (coinPrice == 0) title else if (accessible) "🔓 $title" else "🔒 $title"
        date_upload = Instant.parseOrNull(publishedAt)?.toEpochMilliseconds() ?: 0L
        url = "${manga.url}/chapter/$number"
        scanlator = manga.memo["scanlator"]?.stringOrNull
    }
}

@Serializable
internal data class ChapterContainerDto(
    val chapter: ChapterDto,
    val isUnlocked: Boolean,
)

@Serializable
internal data class ChapterDto(
    val id: String,
    val number: Float,
    val title: String,
    val pages: List<PageDto>,
)

@Serializable
internal data class PageDto(
    val id: String,
    val pageNumber: Int,
    val kind: String,
    val isRedacted: Boolean,
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val isEncrypted: Boolean,
    val hasFragments: Boolean,
    val hasStrips: Boolean,
)
