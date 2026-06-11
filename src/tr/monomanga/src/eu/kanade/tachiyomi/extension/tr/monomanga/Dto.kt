package eu.kanade.tachiyomi.extension.tr.monomanga

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class MangaPageDto(
    val manga: MangaDetailsDto,
    val initialChapters: List<ChapterDto> = emptyList(),
    val initialHasMore: Boolean = false,
)

@Serializable
class MangaDetailsDto(
    @SerialName("_id") val id: String,
    val name: String,
    val slug: String,
    val author: String? = null,
    val artist: String? = null,
    val summary: String? = null,
    val coverImage: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<GenreDto>? = null,
    val volumes: List<VolumeDto>? = null,
)

@Serializable
class GenreDto(val name: String)

@Serializable
class VolumeDto(
    val startChapter: Double,
    val endChapter: Double,
)

@Serializable
class ChapterDto(
    private val title: String,
    private val slug: String,
    private val uploadDate: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/manga/$mangaSlug/$slug"
        name = title
        date_upload = uploadDate?.let { dateFormat.tryParse(it) } ?: 0L
    }
}

@Serializable
class ChapterListResponseDto(
    val data: List<ChapterDto> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: Int? = null,
)

@Serializable
class ChapterPageDto(
    val chapter: ChapterContentDto,
)

@Serializable
class ChapterContentDto(
    val content: List<String>? = null,
)
