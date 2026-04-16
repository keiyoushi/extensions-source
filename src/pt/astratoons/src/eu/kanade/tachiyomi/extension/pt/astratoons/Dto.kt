package eu.kanade.tachiyomi.extension.pt.astratoons

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val hasMore: Boolean,
    val html: String,
)

@Serializable
class ComicsResponseDto(
    val data: List<ComicDto>,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class ComicDto(
    @SerialName("title") private val title: String,
    @SerialName("slug") private val slug: String,
    @SerialName("cover_image") private val coverImage: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@ComicDto.title
        this.thumbnail_url = "$baseUrl/storage/$coverImage"
        this.url = "/comics/$slug"
    }
}
