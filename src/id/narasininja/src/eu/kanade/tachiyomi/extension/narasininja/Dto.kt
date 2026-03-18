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
    val id: Int,
    val title: String,
    val slug: String,
    val detail: DetailDto? = null,
    @SerialName("chapters_down")
    val chaptersDown: List<ChapterDto> = emptyList(),
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@MangaDto.title
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
    val description: String?,
    val status: String?,
    val type: String?,
    val released: String?,
    val author: String?,
    val artist: String?,
)

@Serializable
class ChapterDto(
    val id: Int,
    val slug: String,
    val title: String,
    val url: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
class MetaDto(
    @SerialName("current_page")
    val currentPage: Int,
    @SerialName("last_page")
    val lastPage: Int,
    val total: Int,
)

private val STATUS_ONGOING = Regex("ongoing", RegexOption.IGNORE_CASE)
private val STATUS_COMPLETED = Regex("completed|finished|tamat", RegexOption.IGNORE_CASE)
private val STATUS_HIATUS = Regex("hiatus|on.hold", RegexOption.IGNORE_CASE)
private val STATUS_CANCELLED = Regex("cancelled|canceled|dropped", RegexOption.IGNORE_CASE)

fun String?.toStatus(): Int = when {
    this == null -> SManga.UNKNOWN
    STATUS_ONGOING.containsMatchIn(this) -> SManga.ONGOING
    STATUS_COMPLETED.containsMatchIn(this) -> SManga.COMPLETED
    STATUS_HIATUS.containsMatchIn(this) -> SManga.ON_HIATUS
    STATUS_CANCELLED.containsMatchIn(this) -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
