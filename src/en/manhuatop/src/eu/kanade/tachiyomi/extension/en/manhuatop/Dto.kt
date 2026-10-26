package eu.kanade.tachiyomi.extension.en.manhuatop

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class DiscoveryDto(
    val items: List<CardDto>,
    val hasMore: Boolean,
)

@Serializable
class CardDto(
    private val title: String,
    private val path: String,
    private val thumbnail: String?,
    private val status: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = path
        title = this@CardDto.title
        thumbnail_url = thumbnail
        status = when (this@CardDto.status) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChaptersDto(
    val items: List<ChapterDto>,
    val total: Int,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    private val extra: String?,
    private val date: String?,
    val path: String?,
) {
    fun toSChapter(mangaPath: String) = SChapter.create().apply {
        url = slug
        name = listOfNotNull(title, extra?.takeIf(String::isNotBlank)).joinToString(" - ")
        date_upload = Instant.tryParse(date)
        memo = buildJsonObject { put(MANGA_PATH, mangaPath) }
    }
}

@Serializable
class ContentDto(
    val images: List<ImageDto>,
    val hasMore: Boolean,
)

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class GenresDto(
    val terms: List<TermDto>,
    val hasMore: Boolean,
)

@Serializable
class TermDto(
    val id: String,
    val name: String,
    val count: Int,
)

const val MANGA_PATH = "mangaPath"
