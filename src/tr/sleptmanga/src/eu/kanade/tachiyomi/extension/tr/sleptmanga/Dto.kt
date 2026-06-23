package eu.kanade.tachiyomi.extension.tr.sleptmanga

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class SeriesDto(
    private val name: String,
    private val chapters: List<ChapterDto>,
    private val description: String? = null,
    private val cover: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val owner: OwnerDto? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        this.description = this@SeriesDto.description
        thumbnail_url = cover?.let { if (it.startsWith("http")) it else baseUrl + it }
        this.status = when (this@SeriesDto.status) {
            "Devam Ediyor" -> SManga.ONGOING
            "Tamamlandı" -> SManga.COMPLETED
            "Ara Verildi" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString()
        author = owner?.username
    }

    fun toSChapterList(encodedPath: String): List<SChapter> = chapters.map { it.toSChapter(encodedPath) }.sortedByDescending { it.chapter_number }
}

@Serializable
class OwnerDto(
    val username: String? = null,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val chapterTitle: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(encodedPath: String) = SChapter.create().apply {
        val numStr = number.toString().removeSuffix(".0")
        url = "$encodedPath/$numStr"
        name = chapterTitle?.takeIf { it.isNotBlank() && it != $$"$undefined" }
            ?: "Bölüm $numStr"
        chapter_number = number
        date_upload = chapterDateFormat.tryParse(createdAt?.removePrefix($$"$D"))
    }
}

@Serializable
class ChapterDataDto(
    @SerialName("images_url") private val imagesUrl: List<ImageDto>,
) {
    fun toPageList(baseUrl: String): List<Page> = imagesUrl.mapIndexed { i, img ->
        val imgUrl = if (img.url.startsWith("http")) img.url else baseUrl + img.url
        Page(i, imageUrl = imgUrl)
    }
}

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class PaginationDto(
    private val currentPage: Int,
    private val totalPages: Int,
) {
    val hasNextPage get() = currentPage < totalPages
}
