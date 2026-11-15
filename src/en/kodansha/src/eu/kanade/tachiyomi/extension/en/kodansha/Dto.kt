package eu.kanade.tachiyomi.extension.en.kodansha

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
class SeriesListDto(
    @SerialName("total_count") val totalCount: String,
    val mangas: List<SeriesDto>,
)

@Serializable
class SeriesDto(
    private val uuid: String,
    private val slug: String,
    private val name: String,
    @SerialName("short_description") private val shortDescription: String? = null,
    @SerialName("is_complete") private val isComplete: Boolean? = null,
    private val image: ImageDto? = null,
    private val tags: List<String>? = null,
    private val creators: List<NameDto>? = null,
    private val credits: String? = null,
    @SerialName("alt_titles") private val altTitles: List<NameDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = uuid
        title = name
        thumbnail_url = image?.webp?.maxByOrNull { it.width }?.url
        memo = buildJsonObject { put("slug", slug) }
    }

    fun toSMangaDetails() = toSManga().apply {
        author = creators?.joinToString { it.name }
        genre = tags?.joinToString()
        description = buildString {
            shortDescription?.let { append(it) }
            if (!credits.isNullOrBlank()) append("\n\n", credits)
            if (!altTitles.isNullOrEmpty()) {
                append("\n\nAlternative Titles:")
                altTitles.forEach { append("\n", it.name) }
            }
        }.trim()
        status = if (isComplete == true) SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class ImageDto(val webp: List<ImageSizeDto>)

@Serializable
class ImageSizeDto(
    val url: String,
    val width: Int,
)

@Serializable
class VolumeListDto(val volumes: List<VolumeDto>)

@Serializable
class VolumeDto(
    val uuid: String,
    val label: String,
)

@Serializable
class ChapterListDto(val chapters: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val uuid: String,
    private val label: String,
    private val title: String? = null,
    @SerialName("volume_uuid") val volumeUuid: String? = null,
    @SerialName("release_date") private val releaseDate: String? = null,
    @SerialName("free_published_date") private val freePublishedDate: String? = null,
    @SerialName("free_unpublished_date") private val freeUnpublishedDate: String? = null,
    @SerialName("is_upcoming") private val isUpcoming: Boolean? = null,
) {
    fun isFree(): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        val start = Instant.tryParse(freePublishedDate)
        val end = Instant.tryParse(freeUnpublishedDate)
        return start in 1..now && (end == 0L || end > now)
    }

    fun toSChapter(seriesSlug: String, volume: String?) = SChapter.create().apply {
        url = uuid
        name = buildString {
            if (!isFree()) append("🔒 ")
            if (volume != null) append("Vol. ", volume, " ")
            append("Ch. ", label)
            if (!title.isNullOrBlank()) append(" - ", title)
            if (isUpcoming == true) append(" [Upcoming]")
        }
        chapter_number = label.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(releaseDate)
        memo = buildJsonObject {
            put("slug", seriesSlug)
            put("label", label)
            volume?.let { put("volume", it) }
        }
    }
}

@Serializable
class PageListDto(val data: PageDataDto)

@Serializable
class PageDataDto(val pages: List<PageDto>)

@Serializable
class PageDto(val image: ImageDto)
