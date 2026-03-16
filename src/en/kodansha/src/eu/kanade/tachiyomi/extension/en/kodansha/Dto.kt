package eu.kanade.tachiyomi.extension.en.kodansha

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.String

@Serializable
class EntryResponse(
    val response: List<Entries>,
    val status: Status?,
)

@Serializable
class Status(
    val fullCount: Int?,
)

@Serializable
class Entries(
    val type: String?,
    val content: EntryContent,
)

@Serializable
class EntryContent(
    private val id: Int,
    private val title: String,
    private val thumbnails: List<Thumbnail>?,
    private val readableUrl: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "$readableUrl#$id"
        title = this@EntryContent.title
        thumbnail_url = thumbnails?.last()?.url
    }
}

@Serializable
class Thumbnail(
    val url: String,
)

@Serializable
class DetailsResponse(
    val response: Details,
)

@Serializable
class Details(
    private val genres: List<Genre>?,
    private val creators: List<Creator>?,
    private val completionStatus: String?,
    private val title: String,
    private val description: String?,
    private val ageRating: String?,
    private val thumbnails: List<Thumbnail>?,
    private val publisher: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@Details.title
        author = creators?.joinToString { "${it.title}: ${it.name}" }
        description = buildString {
            this@Details.description?.let {
                append(Jsoup.parse(it).text())
            }
            if (!publisher.isNullOrBlank()) {
                append("\n\nPublisher: $publisher")
            }

            if (!ageRating.isNullOrBlank()) {
                append("\n\n$ageRating")
            }
        }

        genre = genres?.joinToString { it.name }
        thumbnail_url = thumbnails?.first()?.url
        status = when (completionStatus) {
            "Complete" -> SManga.COMPLETED
            "Ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Creator(
    val name: String,
    val title: String,
)

@Serializable
class PurchasedComic(
    val id: Int,
)

@Serializable
class ChapterResponse(
    private val id: Int,
    private val name: String,
    private val publishDate: String?,
    private val readable: Readable?,
    private val variants: List<Variant>?,
    val chapters: List<ChapterResponse>?,
    private val chapterNumber: Int?,
    private val volumeNumber: Int?,
) {
    fun isLocked(purchasedIds: Set<Int>): Boolean {
        val priceType = variants?.firstOrNull()?.priceType
        val isPaid = priceType == "Paid"
        return isPaid && id !in purchasedIds
    }

    fun requiresLogin(isLoggedIn: Boolean): Boolean {
        val priceType = variants?.firstOrNull()?.priceType
        return priceType == "FreeForRegistered" && !isLoggedIn
    }

    fun toSChapter(isLocked: Boolean, requiresLogin: Boolean): SChapter = SChapter.create().apply {
        url = "${this@ChapterResponse.id}#${readable?.seriesReadableUrl}:$volumeNumber:$chapterNumber:${if (requiresLogin) "1" else "0"}"
        name = if (isLocked) "🔒 ${this@ChapterResponse.name}" else this@ChapterResponse.name
        date_upload = dateFormat.tryParse(publishDate)
    }
}

@Serializable
class Readable(
    val seriesReadableUrl: String,
)

@Serializable
class Variant(
    val priceType: String?,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

@Serializable
class ViewerResponse(
    val pageNumber: Int,
    @SerialName("comicID") val comicId: Int,
)

@Serializable
class PageResponse(
    val url: String,
)

@Suppress("unused")
@Serializable
class LoginRequestBody(
    @SerialName("UserName") val userName: String,
    @SerialName("Password") val password: String,
)

@Suppress("unused")
@Serializable
class RefreshRequestBody(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
class LoginResponse(
    @JsonNames("access_token") val accessToken: String,
    @JsonNames("refresh_token") val refreshToken: String,
)
