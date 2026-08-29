package eu.kanade.tachiyomi.extension.ru.astramanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class SearchResponse(val data: SearchData)

@Serializable
class SearchData(
    val titles: List<TitleDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("current_page") val currentPage: Int = 1,
)

@Serializable
class TitleDetailResponse(val data: TitleDto)

@Serializable
class TitleDto(
    val id: Int,
    private val slug: String,
    private val name: String,
    @SerialName("secondary_name") private val secondaryName: String? = null,
    @SerialName("alternative_names") private val alternativeNames: List<String>? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    @SerialName("cover_versions") private val coverVersions: CoverVersions? = null,
    private val description: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val year: Int? = null,
    private val genres: List<Named>? = null,
    private val tags: List<Named>? = null,
    private val publishers: List<Named>? = null,
    @SerialName("publishing_house") private val publishingHouse: Named? = null,
) {
    private fun coverUrl(mediaUrl: String): String? {
        val path = coverImage ?: coverVersions?.mid ?: coverVersions?.high ?: return null
        return "$mediaUrl/$path"
    }

    fun toSManga(mediaUrl: String): SManga = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = coverUrl(mediaUrl)
    }

    fun toSMangaDetails(mediaUrl: String): SManga = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = coverUrl(mediaUrl)
        author = publishingHouse?.name ?: publishers?.firstOrNull()?.name
        description = buildString {
            secondaryName?.takeIf { it.isNotBlank() }?.let { appendLine("Альт. название: $it") }
            alternativeNames?.takeIf { it.isNotEmpty() }
                ?.let { appendLine("Другие названия: ${it.joinToString()}") }
            year?.let { appendLine("Год выпуска: $it") }
            if (isNotEmpty()) appendLine()
            append(this@TitleDto.description?.trim().orEmpty())
        }.trim()
        genre = buildList {
            type?.let { add(typeName(it)) }
            genres?.mapNotNull { it.name }?.let { addAll(it) }
            tags?.mapNotNull { it.name }?.let { addAll(it) }
        }.filter { it.isNotBlank() }.distinct().joinToString()
        status = parseStatus(this@TitleDto.status)
    }
}

@Serializable
class CoverVersions(
    val high: String? = null,
    val mid: String? = null,
)

@Serializable
class Named(val name: String? = null)

@Serializable
class BranchesResponse(val data: BranchesData)

@Serializable
class BranchesData(val branches: List<BranchDto> = emptyList())

@Serializable
class BranchDto(
    val id: Int,
    @SerialName("is_main") val isMain: Boolean? = null,
    @SerialName("count_chapters") val countChapters: Int? = null,
)

@Serializable
class ChaptersResponse(val data: ChaptersData)

@Serializable
class ChaptersData(
    val items: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val id: Long,
    private val number: Float = 0f,
    @SerialName("volume_number") private val volumeNumber: Int? = null,
    private val name: String? = null,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    private fun numberStr(): String = number.toString().removeSuffix(".0")

    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        url = "$slug/${numberStr()}/$id"
        name = buildString {
            if (volumeNumber != null) append("Том $volumeNumber ")
            append("Глава ${numberStr()}")
            this@ChapterDto.name?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
        }
        chapter_number = number
        date_upload = DATE_FORMAT.tryParse(publishedAt)
    }
}

@Serializable
class PagesResponse(val data: PagesData)

@Serializable
class PagesData(val pages: List<PageDto> = emptyList())

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseStatus(status: String?): Int = when (status) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "paused" -> SManga.ON_HIATUS
    "frozen", "discontinued" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private fun typeName(type: String): String = when (type) {
    "manga" -> "Манга"
    "manhwa" -> "Манхва"
    "manhua" -> "Маньхуа"
    "comics" -> "Комикс"
    else -> type
}
