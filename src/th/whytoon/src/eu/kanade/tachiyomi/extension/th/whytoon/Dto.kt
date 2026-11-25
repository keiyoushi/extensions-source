package eu.kanade.tachiyomi.extension.th.whytoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class TrpcInput<T>(private val json: T)

@Serializable
class TrpcResponse<T>(private val result: TrpcResult<T>) {
    val data get() = result.data.json
}

@Serializable
class TrpcResult<T>(val data: TrpcData<T>)

@Serializable
class TrpcData<T>(val json: T)

@Serializable
class SearchQuery(
    private val sort: String,
    private val page: Int,
    private val limit: Int,
    private val q: String? = null,
    private val genre: String? = null,
    private val completionStatus: String? = null,
    private val countryOrigin: String? = null,
    private val ageRating: String? = null,
)

@Serializable
class SearchDto(
    val data: List<ContentDto>,
    private val pagination: PaginationDto,
) {
    val hasNextPage get() = pagination.page < pagination.totalPages
}

@Serializable
class PaginationDto(val page: Int, val totalPages: Int)

@Serializable
class ContentDto(
    private val name: String,
    private val slug: String,
    private val thumbnailImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/content/$slug"
        title = name
        thumbnail_url = thumbnailImage?.let { "${WhyToon.CDN_URL}/$it" }
    }
}

@Serializable
class EpisodeListDto(
    private val contentSlug: String,
    private val episodes: List<EpisodeDto>,
) {
    fun toSChapters() = episodes.sortedByDescending { it.no }.map { it.toSChapter(contentSlug) }
}

@Serializable
class EpisodeDto(
    val no: Double,
    private val name: String? = null,
    private val publishedAt: String? = null,
    private val premium: Boolean = false,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val number = no.toString().removeSuffix(".0")
        url = "/content/$slug/$number"
        name = listOfNotNull("ตอนที่ $number", this@EpisodeDto.name?.trim()?.ifEmpty { null })
            .joinToString(" • ")
            // VIP members read these first, they open to everyone later
            .let { if (premium) "🔒 $it" else it }
        chapter_number = no.toFloat()
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(val data: ReaderDataDto)

@Serializable
class ReaderDataDto(val images: List<String>)
