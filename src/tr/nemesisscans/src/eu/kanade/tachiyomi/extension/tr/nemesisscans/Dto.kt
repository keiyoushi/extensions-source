package eu.kanade.tachiyomi.extension.tr.nemesisscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class SeriesListDto(
    val data: List<SeriesDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    private val page: Int,
    private val pages: Int,
) {
    val hasNextPage get() = page < pages
}

@Serializable
class LatestDto(
    val data: List<LatestEpisodeDto>,
)

@Serializable
class LatestEpisodeDto(
    val series: SeriesDto,
)

@Serializable
class SeriesDetailDto(
    val series: SeriesDto,
    val episodes: List<EpisodeDto>,
)

@Serializable
class SeriesDto(
    @SerialName("_id") val id: Int,
    private val title: TitleDto,
    private val description: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val isFinished: Boolean = false,
    private val poster: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@SeriesDto.title.english ?: this@SeriesDto.title.romaji
        thumbnail_url = poster
        description = this@SeriesDto.description?.let {
            Jsoup.parseBodyFragment(it.replace("\n", "")).wholeText().trim()
        }
        genre = genres.joinToString()
        status = when {
            isFinished || this@SeriesDto.status == "FINISHED" -> SManga.COMPLETED
            this@SeriesDto.status == "RELEASING" -> SManga.ONGOING
            this@SeriesDto.status == "HIATUS" -> SManga.ON_HIATUS
            this@SeriesDto.status == "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class TitleDto(
    val romaji: String,
    val english: String? = null,
)

@Serializable
class EpisodeDto(
    private val episodeNumber: Float,
    private val episodeName: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(seriesId: Int) = SChapter.create().apply {
        val number = episodeNumber.toString().removeSuffix(".0")
        url = "$seriesId/$number"
        name = buildString {
            append("Bölüm ", number)
            episodeName?.takeIf { it.isNotBlank() }?.let { append(" - ", it) }
        }
        chapter_number = episodeNumber
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class EpisodePagesDto(
    val episode: EpisodeImagesDto,
)

@Serializable
class EpisodeImagesDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val url: String,
    val order: Int,
)
