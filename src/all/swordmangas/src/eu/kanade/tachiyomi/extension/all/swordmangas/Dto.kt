package eu.kanade.tachiyomi.extension.all.swordmangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class LatestDto(
    val items: List<SeriesDto>,
    val currentPage: Int,
    val totalPages: Int,
)

@Serializable
class SeriesPageDto(
    val serie: SeriesDto,
    val sortedChapters: List<ChapterDto>,
)

@Serializable
class SeriesDto(
    val slug: String,
    private val title: String,
    @SerialName("title_en") private val titleEn: String? = null,
    private val cover: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    private val synopsis: String? = null,
    @SerialName("synopsis_en") private val synopsisEn: String? = null,
    private val genres: List<String> = emptyList(),
) {
    fun toSManga(english: Boolean) = SManga.create().apply {
        url = slug
        title = if (english) titleEn ?: this@SeriesDto.title else this@SeriesDto.title
        thumbnail_url = cover
        description = if (english) synopsisEn ?: synopsis else synopsis
        genre = (listOfNotNull(type) + genres).joinToString().ifEmpty { null }
        status = this@SeriesDto.status.orEmpty().lowercase().let {
            when {
                "emisi" in it -> SManga.ONGOING
                "final" in it || "complet" in it -> SManga.COMPLETED
                "paus" in it -> SManga.ON_HIATUS
                "cancel" in it -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String,
    private val date: String? = null,
    @SerialName("is_paid_chapter") private val isPaid: Boolean = false,
    @SerialName("can_read") private val canRead: Boolean = true,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "$slug/${number.toString().removeSuffix(".0")}"
        // Early access chapters, to unlock with the site's currency until they turn free
        name = if (isPaid && !canRead) "🔒 $title" else title
        chapter_number = number
        date_upload = Instant.tryParse(date)
    }
}

@Serializable
class ReaderDto(
    val initialChapterData: Map<String, List<String>>,
)
