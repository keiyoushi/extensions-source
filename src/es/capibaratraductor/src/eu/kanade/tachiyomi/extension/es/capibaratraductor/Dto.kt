package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class Data<T>(val data: T)

@Serializable
class SeriesListDataDto(
    @SerialName("items") val series: List<SeriesDto> = emptyList(),
    val maxPage: Int = 0,
)

@Serializable
class SeriesDto(
    val manga: MangaInfoDto,
    private val imageUrl: String? = null,
    private val title: String,
    private val status: String? = null,
    private val description: String? = null,
    val chapters: List<SeriesChapterDto>? = emptyList(),
    val organization: OrganizationDto,
) {
    fun toSManga() = SManga.create().apply {
        title = this@SeriesDto.title
        thumbnail_url = imageUrl
        url = "${manga.slug}/${organization.slug}"
        artist = organization.name
    }

    fun toSMangaDetails() = toSManga().apply {
        status = parseStatus(this@SeriesDto.status)
        description = this@SeriesDto.description
        title = this@SeriesDto.title
        author = manga.authors?.joinToString { it.name }
        artist = organization.name
    }

    private fun parseStatus(status: String?) = when (status) {
        "ongoing" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        "finished" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class OrganizationDto(
    val name: String,
    val slug: String,
)

@Serializable
class MangaInfoDto(
    val slug: String,
    val authors: List<SeriesAuthorDto>? = emptyList(),
)

@Serializable
class SeriesAuthorDto(
    val name: String,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class SeriesChapterDto(
    private val title: String,
    private val number: Float,
    private val releasedAt: String? = null,
    val isUnreleased: Boolean = false,
) {
    fun toSChapter(seriesSlug: String, organizationSlug: String) = SChapter.create().apply {
        name = "Capítulo ${number.toString().removeSuffix(".0")} - $title"
        date_upload = releasedAt.let { dateFormat.tryParse(it) }
        url = "$number/$seriesSlug/$organizationSlug"
    }
}

@Serializable
class PageDto(
    val imageUrl: String,
)

@Serializable
class ScanListDto(
    val items: List<ScanDto> = emptyList(),
    private val page: Int,
    private val maxPage: Int,
) {
    fun hasNextPage() = page < maxPage
}

@Serializable
class ScanDto(
    val id: String,
    val name: String,
)
