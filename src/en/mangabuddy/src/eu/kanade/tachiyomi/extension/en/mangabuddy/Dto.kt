package eu.kanade.tachiyomi.extension.en.mangabuddy

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class SearchResponseDto(
    private val data: SearchDataDto? = null,
) {
    val items get() = data?.items ?: emptyList()
    val hasNext get() = data?.pagination?.hasNext ?: false
}

@Serializable
class SearchDataDto(
    val items: List<MangaItemDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class MangaItemDto(
    private val id: String,
    private val name: String,
    private val cover: String,
    private val url: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = cover
        url = "${this@MangaItemDto.url}#$id"
    }
}

@Serializable
class PaginationDto(
    @SerialName("has_next") val hasNext: Boolean = false,
)

@Serializable
class ChapterListResponseDto(
    private val data: ChapterDataDto? = null,
) {
    val chapters get() = data?.chapters ?: emptyList()
}

@Serializable
class ChapterDataDto(
    val chapters: List<ChapterItemDto> = emptyList(),
)

@Serializable
class ChapterItemDto(
    private val url: String,
    private val name: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
    @SerialName("chapter_number") val chapterNumber: Float? = null,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = this@ChapterItemDto.url
        name = this@ChapterItemDto.name
        date_upload = dateFormat.tryParse(updatedAt)
    }
}

@Serializable
class NextJsDto(
    val pageProps: PagePropsDto,
)

@Serializable
class PagePropsDto(
    val initialManga: InitialMangaDto? = null,
    val initialChapter: InitialChapterDto? = null,
)

@Serializable
class InitialMangaDto(
    val id: String,
    private val name: String,
    private val authors: List<EntityDto>? = null,
    private val summary: String? = null,
    private val genres: List<EntityDto>? = null,
    private val status: String? = null,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = authors?.joinToString { it.name }
        description = buildString {
            if (!summary.isNullOrBlank()) {
                append(summary).append("\n\n")
            }
            append("Manga ID: ").append(id)
        }
        genre = genres?.joinToString { it.name }
        status = this@InitialMangaDto.status.toStatus()
        thumbnail_url = cover
    }
}

@Serializable
class EntityDto(
    val name: String,
)

@Serializable
class InitialChapterDto(
    val images: List<String>,
)

private fun String?.toStatus() = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
