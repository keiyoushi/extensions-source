package eu.kanade.tachiyomi.extension.id.narasininja

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class FilterResponse(
    val data: List<MangaDto>,
    val meta: MetaDto,
)

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    private val detail: DetailDto? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        this.title = this@MangaDto.title
        url = "/komik/$slug"
        thumbnail_url = "$baseUrl/storage/comic/image-bg/$slug.jpg"
        detail?.let {
            description = it.description
            status = it.status.toStatus()
            author = it.author.takeUnless { a -> a.isNullOrBlank() || a == "-" }
            artist = it.artist.takeUnless { a -> a.isNullOrBlank() || a == "-" }
        }
    }
}

@Serializable
class DetailDto(
    val description: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
)

@Serializable
class MetaDto(
    @SerialName("current_page")
    val currentPage: Int,
    @SerialName("last_page")
    val lastPage: Int,
)

private val STATUS_ONGOING = Regex("ongoing", RegexOption.IGNORE_CASE)
private val STATUS_COMPLETED = Regex("completed|finished|tamat", RegexOption.IGNORE_CASE)
private val STATUS_HIATUS = Regex("hiatus|on.hold", RegexOption.IGNORE_CASE)
private val STATUS_CANCELLED = Regex("cancelled|canceled|dropped", RegexOption.IGNORE_CASE)

internal fun String?.toStatus(): Int = when {
    this == null -> SManga.UNKNOWN
    STATUS_ONGOING.containsMatchIn(this) -> SManga.ONGOING
    STATUS_COMPLETED.containsMatchIn(this) -> SManga.COMPLETED
    STATUS_HIATUS.containsMatchIn(this) -> SManga.ON_HIATUS
    STATUS_CANCELLED.containsMatchIn(this) -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
