package eu.kanade.tachiyomi.extension.es.lectormonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicsDataProps(
    val comicsData: ComicsData,
)

@Serializable
class ComicsData(
    val comics: List<ComicDto> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
class ComicDto(
    private val name: String,
    private val urlPath: String? = null,
    @SerialName("comic_path") private val comicPath: String? = null,
    private val urlCover: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val state: String? = null,
    private val genres: List<String>? = null,
    private val description: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = (urlPath ?: comicPath)!!
        thumbnail_url = urlCover ?: coverImage
        this.description = this@ComicDto.description
        genre = genres?.joinToString()
        status = parseStatus(state)
    }
}

@Serializable
class ComicDataProps(
    val comicData: ComicDetailsDto,
)

@Serializable
class ComicDetailsDto(
    private val name: String? = null,
    private val title: String? = null,
    private val description: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val urlCover: String? = null,
    private val state: String? = null,
    private val genres: List<GenreDto>? = null,
    @SerialName("scan_groups") val scanGroups: List<ScanGroupDto>? = null,
    @SerialName("url_pages") val urlPages: List<String>? = null,
) {
    fun toSManga() = SManga.create().apply {
        this.title = (this@ComicDetailsDto.title ?: this@ComicDetailsDto.name)!!
        this.description = this@ComicDetailsDto.description
        thumbnail_url = urlCover ?: coverImage
        genre = genres?.joinToString { it.name }
        status = parseStatus(state)
    }
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ScanGroupDto(
    val name: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter_number") private val chapterNumber: String,
    private val title: String? = null,
    @SerialName("release_date") private val releaseDate: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("chapter_path") private val chapterPath: String? = null,
) {
    fun toSChapter(groupName: String?) = SChapter.create().apply {
        name = this@ChapterDto.title ?: "Capítulo ${chapterNumber.removeSuffix(".0")}"
        url = chapterPath!!
        scanlator = groupName
        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
    }

    val dateString: String? get() = releaseDate ?: createdAt
}

internal fun parseStatus(state: String?): Int = when (state?.uppercase()) {
    "ONGOING" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "HIATUS" -> SManga.ON_HIATUS
    "CANCELLED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
