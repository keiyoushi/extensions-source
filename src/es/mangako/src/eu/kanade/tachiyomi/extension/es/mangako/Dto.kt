package eu.kanade.tachiyomi.extension.es.mangako

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// The API writes "no_tiene" for an empty field
private const val NONE = "no_tiene"

// Chapter dates are in UTC-4, also after the end of daylight saving time
private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val DATE_ZONE = ZoneOffset.ofHours(-4)

@Serializable
class ListDto<T>(
    val datas: List<T>,
    private val pages: List<PageNumberDto>,
) {
    fun hasNextPage(page: Int) = page < pages.maxOf { it.number }
}

@Serializable
class PageNumberDto(val number: Int)

@Serializable
class TitleCardDto(
    private val dataId: Int,
    private val title: String,
    private val photo: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$dataId"
        title = this@TitleCardDto.title
        thumbnail_url = photo
    }
}

// A chapter of the latest chapters feed, or a title of a scan group
@Serializable
class TitleRefDto(
    val titleDataId: Int,
    private val title: String,
    private val photo: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$titleDataId"
        title = this@TitleRefDto.title
        thumbnail_url = photo
    }
}

@Serializable
class ScanDto(val titles: List<TitleRefDto>)

@Serializable
class TitleDto(
    private val datas: TitleDatasDto,
    val chapters: ChapterGroupDto,
) {
    fun toSManga(url: String) = datas.infoTitle.toSManga(url)
}

@Serializable
class TitleDatasDto(val infoTitle: InfoTitleDto)

@Serializable
class InfoTitleDto(
    private val title: String,
    private val names: List<NameDto>,
    private val description: String?,
    private val cover: String?,
    private val status: String?,
    private val genres: List<String>,
    private val author: String?,
    private val type: String?,
    private val puntuation: String?,
    private val votes: Int,
    private val scans: List<ScanNameDto>,
) {
    fun toSManga(url: String) = SManga.create().apply {
        this.url = url
        title = this@InfoTitleDto.title
        thumbnail_url = cover
        author = this@InfoTitleDto.author?.takeUnless { it == NONE }
        genre = (listOfNotNull(type) + genres).joinToString().ifEmpty { null }
        status = when (this@InfoTitleDto.status?.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "terminado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(puntuation?.replace(',', '.')?.toDoubleOrNull()?.takeIf { votes > 0 }, votes = votes)
            .description(this@InfoTitleDto.description)
            .altTitles(names.map { it.name })
            .field("Scans", scans.map { it.scan })
            .build()
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class ScanNameDto(val scan: String)

@Serializable
class TitleChaptersDto(val chapters: ChapterGroupDto)

@Serializable
class ChapterGroupDto(val chapters: ChapterPageDto)

@Serializable
class ChapterPageDto(
    val volume: List<VolumeDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(val totalPages: Int)

@Serializable
class VolumeDto(val chapters: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val dataId: Int,
    private val chapter: String,
    private val title: String?,
    private val publication: String?,
    private val scans: List<ScanNameDto>,
    private val locked: Boolean,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/chapter/$dataId"
        name = buildString {
            if (locked) append("🔒 ")
            append(chapter)
            title?.takeUnless { it == NONE }?.let { append(": ", it) }
        }
        date_upload = DATE_FORMAT.tryParseDateTime(publication, DATE_ZONE)
        scanlator = scans.joinToString { it.scan }.ifEmpty { null }
    }
}

@Serializable
class ReaderDto(
    val images: List<String>,
    val lock: LockDto,
    private val title: ReaderTitleDto,
) {
    val titleId get() = title.titleLink.split('/')[2]
}

@Serializable
class LockDto(val locked: Boolean)

@Serializable
class ReaderTitleDto(val titleLink: String)
