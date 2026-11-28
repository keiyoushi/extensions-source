package eu.kanade.tachiyomi.extension.en.toptoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class CatalogDto(val totalSearch: List<ComicDto>)

@Serializable
class ComicDto(
    val comicId: Int,
    private val information: ComicInfoDto,
    private val author: List<String> = emptyList(),
    val genres: List<String>? = null,
    private val keywords: List<String>? = null,
    private val labels: List<String> = emptyList(),
    private val thumbnailImage: ImageSetDto,
    private val lastEpisode: LastEpisodeDto? = null,
    private val category: List<CategoryDto>? = null,
) {
    val title get() = information.title
    val isComic get() = information.comicType != VIDEO_TYPE
    val viewCount get() = information.viewCount
    val sales get() = information.forDaysSumCoin
    val isCompleted get() = "COMPLETE" in labels
    val updateDays get() = category.orEmpty().filter { it.depth1 == "daily" }.map { it.depth2 }
    val updatedAt get() = Instant.tryParse(lastEpisode?.publishedAt?.date)

    fun matches(query: String) = title.contains(query, ignoreCase = true) ||
        author.any { it.contains(query, ignoreCase = true) } ||
        keywords.orEmpty().any { it.contains(query, ignoreCase = true) }

    fun toSManga() = SManga.create().apply {
        url = "/content/$comicId"
        title = information.title
        thumbnail_url = thumbnailImage.webp?.firstOrNull()?.path
    }

    fun toSMangaDetails() = toSManga().apply {
        author = this@ComicDto.author.joinToString().ifEmpty { null }
        genre = (listOfNotNull("Mature".takeIf { "MATURE" in labels }) + genres.orEmpty() + keywords.orEmpty())
            .distinct().joinToString().ifEmpty { null }
        status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
        description = MarkdownDescription.Builder()
            .rating(information.score)
            .info(
                information.viewCount?.let { "$it views" },
                information.likeCount?.let { "$it likes" },
                "Uncensored".takeIf { information.isUncensored == 1 },
            )
            .description(information.description)
            .build()
    }
}

@Serializable
class ComicInfoDto(
    val title: String,
    val comicType: Int? = null,
    val description: String? = null,
    val score: Double? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val isUncensored: Int? = null,
    val forDaysSumCoin: Int? = null,
)

@Serializable
class CategoryDto(val depth1: String, val depth2: String)

@Serializable
class LastEpisodeDto(val publishedAt: DateDto? = null)

@Serializable
class DateDto(val date: String? = null)

@Serializable
class ImageSetDto(val webp: List<ImageDto>? = null)

@Serializable
class ImageDto(val path: String)

@Serializable
class EpisodePageDto(
    val comic: ComicDto,
    val episode: List<EpisodeDto>,
)

@Serializable
class EpisodeDto(
    private val episodeId: Int,
    private val comicId: Int,
    private val information: EpisodeInfoDto,
    val order: Int,
    private val isPurchased: Int = 0,
    private val isRental: RentalDto? = null,
) {
    val isFree get() = information.coin == 0 || isPurchased == 1 || isRental?.status == 1

    fun toSChapter(locked: Boolean) = SChapter.create().apply {
        url = "/content/$comicId/$episodeId"
        val title = listOfNotNull(information.title, information.subTitle?.takeIf { it.isNotBlank() }).joinToString(": ")
        name = if (locked) "🔒 $title" else title
        date_upload = Instant.tryParse(information.publishedAt?.date)
    }
}

@Serializable
class EpisodeInfoDto(
    val title: String,
    val subTitle: String? = null,
    val coin: Int,
    val publishedAt: DateDto? = null,
)

@Serializable
class RentalDto(val status: Int)

@Serializable
class ViewerDto(val episode: List<ViewerEpisodeDto>)

@Serializable
class ViewerRefusalDto(val needLogin: Boolean? = null)

@Serializable
class ViewerEpisodeDto(
    val episodeId: Int,
    val contentImage: ImageSetDto? = null,
)

@Serializable
class ViewerRequestDto(
    val location: String,
    val action: String,
    val isCached: Boolean,
    val cToken: String,
)

@Serializable
class SessionDto(
    val token: String,
    val userId: Long,
)

private const val VIDEO_TYPE = 3
