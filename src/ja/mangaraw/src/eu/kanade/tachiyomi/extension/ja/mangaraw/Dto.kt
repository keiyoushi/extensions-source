package eu.kanade.tachiyomi.extension.ja.mangaraw

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class MangaListDto(
    private val data: List<MangaDto>,
    private val pagination: PaginationDto? = null,
) {
    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, pagination?.hasMore == true)
}

@Serializable
class PaginationDto(
    val hasMore: Boolean,
)

@Serializable
class MangaDto(
    private val id: Long,
    private val title: String,
    private val image: String? = null,
    private val status: String? = null,
    private val description: String? = null,
    private val alternative: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    private val tags: List<GenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        thumbnail_url = image
        description =
            listOfNotNull(this@MangaDto.description, alternative?.takeIf { it.isNotBlank() })
                .joinToString("\n\n")
        genre = (genres + tags).map { it.name }.distinct().joinToString()
        status =
            when (this@MangaDto.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
    }
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: Long,
    private val title: String,
    private val index: Float,
    private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = title
        chapter_number = index
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ReaderDto(
    private val storageUrl: String,
    private val content: List<ContentDto>,
) {
    fun toPages() = content
        .flatMap { it.imageUrls(storageUrl) }
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}

@Serializable
sealed class ContentDto {
    abstract fun imageUrls(storageUrl: String): List<String>
}

@Serializable
@SerialName("selfhosted")
class SelfHostedContent(
    private val data: ImageTemplateDto,
) : ContentDto() {
    override fun imageUrls(storageUrl: String) = data.imageUrls(storageUrl)
}

@Serializable
class ImageTemplateDto(
    private val from: Int,
    private val to: Int,
    private val url: String,
) {
    fun imageUrls(storageUrl: String) = (from..to).map { storageUrl + url.replace("{page}", it.toString()) }
}

@Serializable
@SerialName("hotlink")
class HotlinkContent(
    private val data: List<String>,
) : ContentDto() {
    override fun imageUrls(storageUrl: String) = data
}

@Serializable
@SerialName("external")
class ExternalContent(
    private val data: List<String>,
) : ContentDto() {
    override fun imageUrls(storageUrl: String) = data.map { storageUrl + it }
}
