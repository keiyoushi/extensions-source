package eu.kanade.tachiyomi.extension.id.keikomik

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ListDto(
    val data: List<ComicDto>,
    val hasNextPage: Boolean,
)

@Serializable
class ComicDto(
    private val slug: String,
    private val name: String,
    private val image: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/komik/$slug"
        title = name
        thumbnail_url = image
    }
}

@Serializable
class SeriesPageDto(
    private val item: SeriesDto,
    private val komikList: List<ChapterDto>,
) {
    fun toSManga() = item.toSManga()

    fun toSChapters() = komikList.map { it.toSChapter(item.slug) }
}

@Serializable
class SeriesDto(
    val slug: String,
    private val name: String,
    private val name2: String? = null,
    private val image: String? = null,
    private val author: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val rilis: String? = null,
    private val rate: Double? = null,
    private val genre: List<String> = emptyList(),
    private val themes: List<String> = emptyList(),
    private val demographic: List<String> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/komik/$slug"
        title = name
        thumbnail_url = image
        author = this@SeriesDto.author?.takeUnless { it.isBlank() }
        genre = (this@SeriesDto.genre + themes + demographic).distinct().joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(rate)
            .info(type, rilis)
            .description(this@SeriesDto.description)
            .altTitles(name2?.split(",").orEmpty())
            .build()
    }
}

@Serializable
class ChapterDto(
    private val id: Double,
    @SerialName("CreateAt") private val createAt: Long? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val number = id.toString().removeSuffix(".0")
        url = "/chapter/$slug-chapter-$number"
        name = "Chapter $number"
        chapter_number = id.toFloat()
        date_upload = createAt ?: 0L
    }
}

@Serializable
class ChapterPageDto(
    val subItem: ChapterImagesDto,
)

@Serializable
class ChapterImagesDto(
    val img: List<String> = emptyList(),
)
