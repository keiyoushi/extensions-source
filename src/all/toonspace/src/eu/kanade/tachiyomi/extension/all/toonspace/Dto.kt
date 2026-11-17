package eu.kanade.tachiyomi.extension.all.toonspace

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ListDto<T>(
    val data: List<T>,
    private val meta: MetaDto,
) {
    val hasNextPage get() = meta.pagination.page < meta.pagination.pageCount
}

@Serializable
class DataDto<T>(
    val data: T,
)

@Serializable
class MetaDto(
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    val page: Int,
    val pageCount: Int,
)

@Serializable
class ToonDto(
    private val title: String,
    private val urlSlug: String,
    private val description: String? = null,
    private val author: String? = null,
    private val isComplete: Boolean? = null,
    private val image: ImageDto? = null,
    private val genres: List<GenreDto>? = null,
    val episodesCount: Int? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = urlSlug
        title = this@ToonDto.title
        thumbnail_url = image?.url
        author = this@ToonDto.author
        description = MarkdownDescription.Builder()
            .description(this@ToonDto.description)
            .build()
        genre = genres?.joinToString { GENRES[it.type] ?: it.type }?.ifEmpty { null }
        status = when (isComplete) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            null -> SManga.UNKNOWN
        }
        if (isComplete == true) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class GenreDto(
    val type: String,
)

@Serializable
class LatestEpisodeDto(
    val toon: ToonDto? = null,
)

@Serializable
class EpisodeDto(
    private val documentId: String,
    private val index: Int,
    private val title: String,
    private val releasedAt: String? = null,
    private val pages: List<PageDto>,
    private val toon: EpisodeToonDto,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/r/${toon.documentId}/$documentId/$index"
        val episode = title.removePrefix("${toon.title} - ")
        // The API sends the pages of the free episodes only
        name = if (pages.isEmpty()) "$LOCK $episode" else episode
        date_upload = Instant.tryParse(releasedAt)
    }
}

@Serializable
class EpisodeToonDto(
    val documentId: String,
    val title: String,
)

@Serializable
class EpisodePagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val url: String,
)

const val LOCK = "🔒"
