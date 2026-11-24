package eu.kanade.tachiyomi.extension.tr.mangacix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class TitlesResponseDto(
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    val data: List<TitleListItemDto>,
)

@Serializable
class TitleListItemDto(
    private val id: Long,
    private val name: String,
    private val poster: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = poster
    }
}

@Serializable
class LastEpisodesResponseDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    val data: List<LastEpisodeDto>,
)

@Serializable
class LastEpisodeDto(
    @SerialName("title_id") val titleId: Long,
    @SerialName("title_name") val titleName: String,
    @SerialName("title_poster") val titlePoster: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        thumbnail_url = titlePoster
    }
}

@Serializable
class TitleDetailsResponseDto(
    val title: TitleDetailsDto,
)

@Serializable
class TitleDetailsDto(
    private val id: Long,
    private val name: String,
    private val poster: String? = null,
    private val description: String? = null,
    @SerialName("series_ended") private val seriesEnded: Boolean? = null,
    private val genres: List<DisplayNameDto>? = null,
    private val keywords: List<DisplayNameDto>? = null,
    private val credits: List<CreditDto>? = null,
    val episodes: List<EpisodeDto>? = null,
    @SerialName("episode_images") val episodeImages: List<EpisodeImageDto>? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = poster
        description = this@TitleDetailsDto.description
        status = when (seriesEnded) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            null -> SManga.UNKNOWN
        }
        val allGenres = buildList {
            genres?.forEach { it.displayName?.let(::add) }
            keywords?.forEach { it.displayName?.let(::add) }
        }.distinct()
        if (allGenres.isNotEmpty()) {
            genre = allGenres.joinToString()
        }
        val authorNames = credits
            ?.filter { it.pivot?.department.equals("writing", ignoreCase = true) || it.knownFor.equals("writing", ignoreCase = true) }
            ?.mapNotNull { it.name?.ifBlank { null } }
            ?.distinct()
            ?.ifEmpty { credits.firstOrNull()?.name?.ifBlank { null }?.let(::listOf) }

        if (!authorNames.isNullOrEmpty()) {
            author = authorNames.joinToString()
        }
    }
}

@Serializable
class DisplayNameDto(
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
class CreditDto(
    val name: String? = null,
    @SerialName("known_for") val knownFor: String? = null,
    val pivot: CreditPivotDto? = null,
)

@Serializable
class CreditPivotDto(
    val department: String? = null,
)

@Serializable
class EpisodeDto(
    @SerialName("title_id") private val titleId: Long,
    @SerialName("episode_number") val episodeNumber: Float,
    private val name: String? = null,
    @SerialName("release_date") private val releaseDate: String? = null,
    private val translators: List<TranslatorWrapperDto>? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        val epStr = episodeNumber.toString().removeSuffix(".0")
        url = "$titleId/$epStr"
        name = this@EpisodeDto.name?.ifBlank { null } ?: "$epStr. Bölüm"
        chapter_number = episodeNumber
        date_upload = releaseDate?.let { Instant.tryParse(it) } ?: 0L
        val scanlators = translators?.mapNotNull { it.translatorDetails?.name?.ifBlank { null } }?.distinct()
        if (!scanlators.isNullOrEmpty()) {
            scanlator = scanlators.joinToString()
        }
    }
}

@Serializable
class TranslatorWrapperDto(
    val translatorDetails: TranslatorDetailsDto? = null,
)

@Serializable
class TranslatorDetailsDto(
    val name: String? = null,
)

@Serializable
class EpisodeImageDto(
    @SerialName("item_order") val itemOrder: Int = 0,
    val url: String,
)
