package eu.kanade.tachiyomi.extension.es.mangako

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SearchResponse(
    val datas: List<SearchItem> = emptyList(),
    val pages: List<PageInfo> = emptyList(),
)

@Serializable
class SearchItem(
    private val dataId: Int,
    private val title: String,
    private val photo: String? = null,
    private val author: String? = null,
    // Present on type=chapters listings; absent on popular/recent/search
    private val titleDataId: Int? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        // Chapter feed: dataId is chapter id, titleDataId is manga id
        url = (titleDataId ?: dataId).toString()
        this.title = title
        thumbnail_url = photo
        this.author = author
    }
}

@Serializable
class PageInfo(
    val number: Int,
    val selected: Boolean = false,
)

@Serializable
class TitleResponse(
    val datas: TitleData,
    val chapters: ChaptersWrapper,
)

@Serializable
class TitleData(
    val infoTitle: TitleInfo,
)

@Serializable
class TitleInfo(
    private val title: String,
    private val description: String? = null,
    private val cover: String? = null,
    private val status: String? = null,
    private val author: String? = null,
    private val genres: List<String> = emptyList(),
) {
    fun toSManga(id: String): SManga = SManga.create().apply {
        url = id
        this.title = title
        this.description = description
        thumbnail_url = cover
        this.author = author
        genre = genres.joinToString()
        this.status = when (status?.lowercase()) {
            "en emisión", "en emision" -> SManga.ONGOING
            "finalizado", "completo" -> SManga.COMPLETED
            "hiatus", "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChaptersWrapper(
    val chapters: VolumesWrapper,
)

@Serializable
class VolumesWrapper(
    val volume: List<VolumeDto> = emptyList(),
)

@Serializable
class VolumeDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    private val dataId: Int,
    private val chapter: String,
    private val title: String? = null,
    private val publication: String? = null,
    private val scans: List<ScanDto> = emptyList(),
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = dataId.toString()
        name = buildString {
            append(chapter)
            val subtitle = title?.takeIf { it.isNotBlank() && it != "no_tiene" }
            if (subtitle != null) {
                append(" - ")
                append(subtitle)
            }
        }
        date_upload = publication?.let { DATE_TIME_FORMAT.tryParseDateTime(it, ZONE) } ?: 0L
        scanlator = scans.mapNotNull { it.scan }.distinct().joinToString().ifBlank { null }
    }
}

@Serializable
class ScanDto(
    val scan: String? = null,
)

@Serializable
class ChapterResponse(
    val images: List<String> = emptyList(),
    val title: ChapterTitleInfo? = null,
)

@Serializable
class ChapterTitleInfo(
    // e.g. "/title/35/onryo-biyori"
    val titleLink: String? = null,
)

private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
private val ZONE = ZoneId.of("UTC")
