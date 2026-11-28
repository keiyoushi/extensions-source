package eu.kanade.tachiyomi.extension.ko.lezhincomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale
import kotlin.time.Duration.Companion.hours

@Serializable
class ListDto<T>(
    val data: List<T>,
    val hasNext: Boolean = false,
)

@Serializable
class ContentDto(
    private val id: Long,
    private val alias: String,
    private val title: String,
    private val updatedAt: Long,
    private val artists: List<ArtistDto>,
    private val contentsState: String? = null,
    private val tags: List<String> = emptyList(),
    val adult: Boolean = false,
) {
    fun toSManga() = SManga.create().apply {
        url = alias
        title = this@ContentDto.title
        thumbnail_url = thumbnailUrl(id, updatedAt)
        author = artists.authors()
        artist = artists.painters()
        genre = tags.ifEmpty { null }?.joinToString()
        status = parseStatus(contentsState)
    }
}

private fun parseStatus(state: String?) = when (state) {
    "scheduled" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

private fun Long.grouped() = String.format(Locale.ROOT, "%,d", this)

@Serializable
class ArtistDto(
    val name: String,
    val role: String,
)

private fun List<ArtistDto>.authors() = filter { it.role in AUTHOR_ROLES }.joinToString { it.name }.ifEmpty { null }

private fun List<ArtistDto>.painters() = filter { it.role in ARTIST_ROLES }.joinToString { it.name }.ifEmpty { null }

private val AUTHOR_ROLES = setOf("writer", "scripter", "original")
private val ARTIST_ROLES = setOf("writer", "painter")

private fun thumbnailUrl(id: Long, updatedAt: Long) = "https://ccdn.lezhin.com/v2/comics".toHttpUrl().newBuilder()
    .addPathSegment(id.toString())
    .addPathSegments("images/tall.webp")
    .addQueryParameter("updated", updatedAt.toString())
    .build()
    .toString()

@Serializable
class ComicPageDto(
    private val content: ComicDto,
    val episodes: List<EpisodeDto>,
) {
    fun related() = content.related.map { it.toSManga() }

    fun toSManga(manga: SManga) = manga.apply {
        title = content.display.title
        thumbnail_url = thumbnailUrl(content.id, content.updatedAt)
        author = content.artists.authors()
        artist = content.artists.painters()
        genre = content.properties.tags.ifEmpty { null }?.joinToString()
        status = parseStatus(content.state)
        description = MarkdownDescription.Builder()
            .info(
                content.display.schedule,
                content.properties.viewCount?.let { "조회 ${it.grouped()}" },
                content.properties.subscriptions?.let { "구독 ${it.grouped()}" },
            )
            .description(content.display.synopsis)
            .field("공지", content.display.notice)
            .field("출판사", content.artists.filter { it.role == "publisher" }.map { it.name })
            .field("저작권", content.metadata.copyright)
            .build()
    }
}

@Serializable
class ComicDto(
    val id: Long,
    val updatedAt: Long,
    val state: String,
    val artists: List<ArtistDto>,
    val display: ComicDisplayDto,
    val properties: ComicPropertiesDto,
    val metadata: ComicMetadataDto,
    val related: List<ContentDto> = emptyList(),
)

@Serializable
class ComicDisplayDto(
    val title: String,
    val synopsis: String? = null,
    val schedule: String? = null,
    val notice: String? = null,
)

@Serializable
class ComicPropertiesDto(
    val tags: List<String>,
    val viewCount: Long? = null,
    val subscriptions: Long? = null,
)

@Serializable
class ComicMetadataDto(
    val copyright: String? = null,
)

@Serializable
class EpisodeDto(
    @SerialName("name") private val key: String,
    private val coin: Int,
    private val publishedAt: Long,
    private val freedAt: Long? = null,
    private val openedAt: Long? = null,
    private val display: EpisodeDisplayDto,
    private val properties: EpisodePropertiesDto,
) {
    // The site's reader rule, with its two hours of leeway on the free and open dates
    fun isFree(now: Long) = coin == 0 ||
        display.type == "p" ||
        listOfNotNull(freedAt, openedAt).any { it < now + FREE_LEEWAY }

    private fun lockMark(now: Long) = when {
        isFree(now) -> null
        properties.expired -> "⛔"
        properties.notForSale -> "🚫"
        freedAt != null -> "⏳"
        else -> "🔒"
    }

    fun toSChapter(alias: String, now: Long) = SChapter.create().apply {
        url = "$alias/$key"
        name = buildString {
            lockMark(now)?.let { append(it).append(' ') }
            val title = display.title?.ifEmpty { null }
            when {
                title == null -> append(display.displayName)
                title.repeats(display.displayName) -> append(title)
                else -> append(display.displayName).append(' ').append(title)
            }
        }
        // The site's episode list shows when an episode became free
        date_upload = freedAt?.takeIf { it < now + FREE_LEEWAY } ?: publishedAt
        // Regular episodes ("g"); prologues, notices, epilogues and extras have no number
        if (display.type == "g") key.toFloatOrNull()?.let { chapter_number = it }
    }

    // Titles like "429화", "프롤로그" or "외전 : ..." that already carry the display name
    private fun String.repeats(displayName: String): Boolean {
        if (displayName.any { !it.isDigit() }) return startsWith(displayName)
        return filterNot(Char::isWhitespace).removeSuffix("화").trimStart('0') == displayName.trimStart('0')
    }
}

private val FREE_LEEWAY = 2.hours.inWholeMilliseconds

@Serializable
class EpisodePropertiesDto(
    val expired: Boolean = false,
    val notForSale: Boolean = false,
)

@Serializable
class EpisodeDisplayDto(
    val displayName: String,
    val title: String? = null,
    val type: String,
)

@Serializable
class ViewerStateDto(
    val episode: ViewerEpisodeDto,
    val media: MediaDto,
)

@Serializable
class ViewerEpisodeDto(
    val id: Long,
    val updatedAt: Long,
)

@Serializable
class MediaDto(
    val imageShuffle: Boolean,
    val scrollView: List<CutDto> = emptyList(),
    val pageView: List<CutDto> = emptyList(),
)

@Serializable
class CutDto(
    val path: String,
    val cutType: String? = null,
    val isGif: Boolean = false,
)

@Serializable
class PermissionDto(
    val isCollected: Boolean,
)

@Serializable
class SignedUrlDto(
    @SerialName("Policy") val policy: String,
    @SerialName("Signature") val signature: String,
    @SerialName("Key-Pair-Id") val keyPairId: String,
)

@Serializable
class GenreDto(
    val id: String,
    val label: String,
)

@Serializable
class ErrorDto(
    val code: Int,
    val description: String,
)
