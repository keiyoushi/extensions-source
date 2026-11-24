package eu.kanade.tachiyomi.extension.tr.mangatepesi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class MangaDto(
    private val mangaId: Int,
    private val title: String,
    private val titleUrl: String,
    private val description: String? = null,
    private val imgUrl: String? = null,
    private val mangaType: String? = null,
    private val mangaStatus: String? = null,
    private val mangaCategories: String? = null,
    private val mangaStartDate: String? = null,
) {
    val url get() = "/manga/$titleUrl/$mangaId"

    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = this@MangaDto.title.trim()
        thumbnail_url = imgUrl
    }

    fun toSMangaDetails() = toSManga().apply {
        genre = (listOfNotNull(mangaType) + mangaCategories.orEmpty().split(","))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString().ifEmpty { null }
        status = when (mangaStatus?.trim()) {
            "Devam Ediyor", "Güncel" -> SManga.ONGOING
            "Tamamlandı" -> SManga.COMPLETED
            "Bırakıldı" -> SManga.CANCELLED
            "Sezon Finali" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(this@MangaDto.description)
            .field("Yıl", mangaStartDate)
            .build()
    }
}

@Serializable
class MangaDetailsDto(
    val manga: MangaDto,
    private val chapters: List<ChapterDto>,
) {
    fun toSChapters() = chapters.map { it.toSChapter(manga) }.reversed()
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val chapterName: String,
    private val chapterNameUrl: String,
    private val createdAt: String? = null,
) {
    fun toSChapter(manga: MangaDto) = SChapter.create().apply {
        url = "${manga.url.substringBeforeLast('/')}/$chapterNameUrl/$id"
        name = chapterName.trim()
        date_upload = DATE_FORMAT.tryParseDateTime(createdAt, ZONE)
    }
}

@Serializable
class ChapterPagesDto(
    val manga: MangaDto,
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(val imgUrl: String)

@Serializable
class HomeDto(val lastFifteenChapters: List<HomeChapterDto>)

@Serializable
class HomeChapterDto(val manga: MangaDto)

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
private val ZONE = ZoneId.of("Europe/Istanbul")
