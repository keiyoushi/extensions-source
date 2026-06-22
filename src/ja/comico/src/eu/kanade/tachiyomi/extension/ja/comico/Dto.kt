package eu.kanade.tachiyomi.extension.ja.comico

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ComicoResponseDto(
    val result: ComicoResultDto? = null,
    val data: ComicoDataDto? = null,
)

@Serializable
class ComicoResultDto(
    val code: Int = 0,
)

@Serializable
class ComicoDataDto(
    val page: ComicoPageDto? = null,
    val contents: List<ContentInfo>? = null,
    val episode: ComicoEpisodeDto? = null,
    val chapter: ComicoChapterImagesDto? = null,
)

@Serializable
class ComicoPageDto(
    val hasNext: Boolean = false,
)

@Serializable
class ComicoEpisodeDto(
    val content: ContentInfo? = null,
)

@Serializable
class ComicoChapterImagesDto(
    val images: List<ChapterImage> = emptyList(),
)

@Serializable
class ContentInfo(
    val id: Int,
    private val name: String,
    private val description: String? = null,
    private val original: Boolean? = false,
    private val exclusive: Boolean? = false,
    private val mature: Boolean? = false,
    private val status: String? = null,
    private val genres: List<Genre>? = null,
    private val authors: List<Author>? = null,
    private val thumbnails: List<Thumbnail>? = null,
    val chapters: List<Chapter>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@ContentInfo.name
        url = "/comic/$id"
        thumbnail_url = thumbnails?.firstOrNull()?.url
        description = this@ContentInfo.description
        status = when (this@ContentInfo.status) {
            "completed" -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        author = authors?.filter { it.isAuthor }?.joinToString { it.name }
        artist = authors?.filter { it.isArtist }?.joinToString { it.name }
        genre = buildString {
            genres?.joinTo(this) { it.name }
            if (this@ContentInfo.mature == true) {
                if (isNotEmpty()) append(", ")
                append("Mature")
            }
            if (this@ContentInfo.original == true) {
                if (isNotEmpty()) append(", ")
                append("Original")
            }
            if (this@ContentInfo.exclusive == true) {
                if (isNotEmpty()) append(", ")
                append("Exclusive")
            }
        }
    }
}

@Serializable
class Thumbnail(val url: String)

@Serializable
class Author(val name: String, private val role: String) {
    val isAuthor: Boolean
        get() = role == "creator" || role == "writer" || role == "original_creator"
    val isArtist: Boolean
        get() = role == "creator" || role == "artist" || role == "studio" || role == "assistant"
}

@Serializable
class Genre(val name: String)

@Serializable
class Chapter(
    private val id: Int,
    private val name: String,
    private val publishedAt: String? = null,
    private val salesConfig: SalesConfig? = null,
    private val hasTrial: Boolean? = false,
    private val activity: Activity? = null,
) {
    private val isAvailable: Boolean
        get() = salesConfig?.free == true || hasTrial == true || activity?.owned == true

    fun toSChapter(comicId: Int) = SChapter.create().apply {
        url = "/comic/$comicId/chapter/$id/product"
        name = this@Chapter.name + if (isAvailable) "" else Comico.LOCK
        date_upload = dateFormat.tryParse(publishedAt)
        // Chapter number intentionally omitted so it falls back to ChapterRecognition on the `name`
    }
}

@Serializable
class SalesConfig(val free: Boolean? = false)

@Serializable
class Activity(private val rented: Boolean? = false, private val unlocked: Boolean? = false) {
    val owned: Boolean
        get() = rented == true || unlocked == true
}

@Serializable
class ChapterImage(
    val url: String,
    val parameter: String? = null,
)
