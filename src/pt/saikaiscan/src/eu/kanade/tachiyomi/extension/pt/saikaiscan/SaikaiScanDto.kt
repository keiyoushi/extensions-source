package eu.kanade.tachiyomi.extension.pt.saikaiscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class SaikaiScanResultDto<T>(
    val data: T? = null,
    val meta: SaikaiScanMetaDto? = null,
) {

    val hasNextPage: Boolean
        get() = meta !== null && meta.currentPage < meta.lastPage
}

typealias SaikaiScanPaginatedStoriesDto = SaikaiScanResultDto<List<SaikaiScanStoryDto>>
typealias SaikaiScanReleaseResultDto = SaikaiScanResultDto<SaikaiScanReleaseDto>

@Serializable
data class SaikaiScanMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
data class SaikaiScanStoryDto(
    val artists: List<SaikaiScanPersonDto> = emptyList(),
    val authors: List<SaikaiScanPersonDto> = emptyList(),
    val genres: List<SaikaiScanGenreDto> = emptyList(),
    val image: String,
    val releases: List<SaikaiScanReleaseDto> = emptyList(),
    val slug: String,
    val status: SaikaiScanStatusDto? = null,
    val synopsis: String,
    val title: String,
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@SaikaiScanStoryDto.title
        author = authors.joinToString { it.name }
        artist = artists.joinToString { it.name }
        genre = genres.joinToString { it.name }
        status = when (this@SaikaiScanStoryDto.status?.name) {
            "Concluído" -> SManga.COMPLETED
            "Em Andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = Jsoup.parseBodyFragment(synopsis)
            .select("p")
            .joinToString("\n\n") { it.text() }
        thumbnail_url = "${SaikaiScan.IMAGE_SERVER_URL}/$image"
        url = "/comics/$slug"
    }
}

@Serializable
data class SaikaiScanPersonDto(
    val name: String,
)

@Serializable
data class SaikaiScanGenreDto(
    val name: String,
)

@Serializable
data class SaikaiScanStatusDto(
    val name: String,
)

@Serializable
data class SaikaiScanReleaseDto(
    val chapter: String,
    val id: Int,
    @SerialName("is_active") val isActive: Int = 1,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("release_images") val releaseImages: List<SaikaiScanReleaseImageDto> = emptyList(),
    val slug: String,
    val title: String? = "",
) {

    fun toSChapter(storySlug: String): SChapter = SChapter.create().apply {
        name = "Capítulo $chapter" +
            (if (this@SaikaiScanReleaseDto.title.isNullOrEmpty().not()) " - ${this@SaikaiScanReleaseDto.title}" else "")
        chapter_number = chapter.toFloatOrNull() ?: -1f
        date_upload = runCatching { DATE_FORMATTER.parse(publishedAt)?.time }
            .getOrNull() ?: 0L
        scanlator = SaikaiScan.SOURCE_NAME
        url = "/ler/comics/$storySlug/$id/$slug"
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale("pt", "BR"))
        }
    }
}

@Serializable
data class SaikaiScanReleaseImageDto(val image: String)
