package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class YomuMangasHomeDto(
    val updates: List<YomuMangasSeriesDto> = emptyList(),
    val votes: List<YomuMangasSeriesDto> = emptyList(),
)

@Serializable
data class YomuMangasSearchDto(
    val mangas: List<YomuMangasSeriesDto> = emptyList(),
    val page: Int,
    val pages: Int,
) {

    val hasNextPage: Boolean
        get() = page < pages
}

@Serializable
data class YomuMangasDetailsDto(val manga: YomuMangasSeriesDto)

@Serializable
data class YomuMangasSeriesDto(
    val id: Int,
    val slug: String,
    val title: String,
    val cover: String? = null,
    val status: String,
    val authors: List<String>? = emptyList(),
    val artists: List<String>? = emptyList(),
    val genres: List<YomuMangasGenreDto>? = emptyList(),
    val description: String? = null,
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@YomuMangasSeriesDto.title
        author = authors.orEmpty().joinToString { it.trim() }
        artist = artists.orEmpty().joinToString { it.trim() }
        genre = genres.orEmpty()
            .sortedBy { it.name }
            .joinToString { it.name.trim() }
        description = this@YomuMangasSeriesDto.description?.trim()
        status = when (this@YomuMangasSeriesDto.status) {
            "RELEASING" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            "TRANSLATING" -> SManga.PUBLISHING_FINISHED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = cover?.let { "${YomuMangas.CDN_URL}/$it" }
        url = "/manga/$id/$slug"
    }
}

@Serializable
data class YomuMangasGenreDto(val name: String)

@Serializable
data class YomuMangasChaptersDto(val chapters: List<YomuMangasChapterDto> = emptyList())

@Serializable
data class YomuMangasChapterDto(
    val id: Int,
    val chapter: Float,
    @SerialName("uploaded_at") val uploadedAt: String,
    val images: List<YomuMangasImageDto>? = emptyList(),
) {

    fun toSChapter(series: YomuMangasSeriesDto): SChapter = SChapter.create().apply {
        name = "Capítulo ${chapter.toString().removeSuffix(".0")}"
        date_upload = runCatching { DATE_FORMATTER.parse(uploadedAt)?.time }
            .getOrNull() ?: 0L
        url = "/manga/${series.id}/${series.slug}/chapter/$id#$chapter"
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        }
    }
}

@Serializable
data class YomuMangasChapterDetailsDto(val chapter: YomuMangasChapterDto)

@Serializable
data class YomuMangasImageDto(val uri: String)
