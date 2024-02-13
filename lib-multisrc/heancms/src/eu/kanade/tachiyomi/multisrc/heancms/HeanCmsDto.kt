package eu.kanade.tachiyomi.multisrc.heancms

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms.SlugStrategy
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

@Serializable
data class HeanCmsQuerySearchDto(
    val data: List<HeanCmsSeriesDto> = emptyList(),
    val meta: HeanCmsQuerySearchMetaDto? = null,
)

@Serializable
data class HeanCmsQuerySearchMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
) {

    val hasNextPage: Boolean
        get() = currentPage < lastPage
}

@Serializable
data class HeanCmsSearchDto(
    val description: String? = null,
    @SerialName("series_slug") var slug: String,
    @SerialName("series_type") val type: String,
    val title: String,
    val thumbnail: String? = null,
) {

    fun toSManga(
        apiUrl: String,
        coverPath: String,
        mangaSubDirectory: String,
        slugMap: Map<String, HeanCms.HeanCmsTitle>,
        slugStrategy: SlugStrategy,
    ): SManga = SManga.create().apply {
        val slugOnly = slug.toPermSlugIfNeeded(slugStrategy)
        val thumbnailFileName = slugMap[slugOnly]?.thumbnailFileName
        title = this@HeanCmsSearchDto.title
        thumbnail_url = thumbnail?.toAbsoluteThumbnailUrl(apiUrl, coverPath)
            ?: thumbnailFileName?.toAbsoluteThumbnailUrl(apiUrl, coverPath)
        url = "/$mangaSubDirectory/$slugOnly"
    }
}

@Serializable
data class HeanCmsSeriesDto(
    val id: Int,
    @SerialName("series_slug") val slug: String,
    @SerialName("series_type") val type: String = "Comic",
    val author: String? = null,
    val description: String? = null,
    val studio: String? = null,
    val status: String? = null,
    val thumbnail: String,
    val title: String,
    val tags: List<HeanCmsTagDto>? = emptyList(),
    val chapters: List<HeanCmsChapterDto>? = emptyList(),
    val seasons: List<HeanCmsSeasonsDto>? = emptyList(),
) {

    fun toSManga(
        apiUrl: String,
        coverPath: String,
        mangaSubDirectory: String,
        slugStrategy: SlugStrategy,
    ): SManga = SManga.create().apply {
        val descriptionBody = this@HeanCmsSeriesDto.description?.let(Jsoup::parseBodyFragment)
        val slugOnly = slug.toPermSlugIfNeeded(slugStrategy)

        title = this@HeanCmsSeriesDto.title
        author = this@HeanCmsSeriesDto.author?.trim()
        artist = this@HeanCmsSeriesDto.studio?.trim()
        description = descriptionBody?.select("p")
            ?.joinToString("\n\n") { it.text() }
            ?.ifEmpty { descriptionBody.text().replace("\n", "\n\n") }
        genre = tags.orEmpty()
            .sortedBy(HeanCmsTagDto::name)
            .joinToString { it.name }
        thumbnail_url = thumbnail.ifEmpty { null }
            ?.toAbsoluteThumbnailUrl(apiUrl, coverPath)
        status = this@HeanCmsSeriesDto.status?.toStatus() ?: SManga.UNKNOWN
        url = if (slugStrategy != SlugStrategy.NONE) {
            "/$mangaSubDirectory/$slugOnly#$id"
        } else {
            "/$mangaSubDirectory/$slug"
        }
    }
}

@Serializable
data class HeanCmsSeasonsDto(
    val index: Int,
    val chapters: List<HeanCmsChapterDto>? = emptyList(),
)

@Serializable
data class HeanCmsTagDto(val name: String)

@Serializable
data class HeanCmsChapterDto(
    val id: Int,
    @SerialName("chapter_name") val name: String,
    @SerialName("chapter_slug") val slug: String,
    val index: String,
    @SerialName("created_at") val createdAt: String,
    val price: Int? = null,
) {
    fun toSChapter(
        seriesSlug: String,
        mangaSubDirectory: String,
        dateFormat: SimpleDateFormat,
        slugStrategy: SlugStrategy,
    ): SChapter = SChapter.create().apply {
        val seriesSlugOnly = seriesSlug.toPermSlugIfNeeded(slugStrategy)
        name = this@HeanCmsChapterDto.name.trim()

        if (price != 0) {
            name += " \uD83D\uDD12"
        }

        date_upload = runCatching { dateFormat.parse(createdAt)?.time }
            .getOrNull() ?: 0L

        val paidStatus = if (price != 0 && price != null) "-paid" else ""

        url = "/$mangaSubDirectory/$seriesSlugOnly/$slug#$id$paidStatus"
    }
}

@Serializable
data class HeanCmsReaderDto(
    val content: HeanCmsReaderContentDto? = null,
)

@Serializable
data class HeanCmsReaderContentDto(
    val images: List<String>? = emptyList(),
)

@Serializable
data class HeanCmsQuerySearchPayloadDto(
    val order: String,
    val page: Int,
    @SerialName("order_by") val orderBy: String,
    @SerialName("series_status") val status: String? = null,
    @SerialName("series_type") val type: String,
    @SerialName("tags_ids") val tagIds: List<Int> = emptyList(),
)

@Serializable
data class HeanCmsSearchPayloadDto(val term: String)

private fun String.toAbsoluteThumbnailUrl(apiUrl: String, coverPath: String): String {
    return if (startsWith("https://")) this else "$apiUrl/$coverPath$this"
}

private fun String.toPermSlugIfNeeded(slugStrategy: SlugStrategy): String {
    return if (slugStrategy != SlugStrategy.NONE) {
        this.replace(HeanCms.TIMESTAMP_REGEX, "")
    } else {
        this
    }
}

fun String.toStatus(): Int = when (this) {
    "Ongoing" -> SManga.ONGOING
    "Hiatus" -> SManga.ON_HIATUS
    "Dropped" -> SManga.CANCELLED
    "Completed", "Finished" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}
