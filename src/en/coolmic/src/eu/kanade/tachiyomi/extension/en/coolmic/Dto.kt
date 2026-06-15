package eu.kanade.tachiyomi.extension.en.coolmic

import eu.kanade.tachiyomi.extension.en.coolmic.Coolmic.Companion.SEARCH_SIZE
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.flatten

@Serializable
class SeriesResponse(
    val hits: Hits,
)

@Serializable
class Hits(
    val found: Int,
    val start: Int,
    val hit: List<Hit>,
) {
    fun hasNextPage() = start + SEARCH_SIZE < found
}

@Serializable
class Hit(
    val fields: Fields,
)

@Serializable
class Fields(
    @SerialName("title_id") private val titleId: String,
    @SerialName("title_name") private val titleName: String,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = titleId
        title = titleName
        val id = titleId.padStart(9, '0')
        thumbnail_url = "$cdnUrl/titles/${id.take(3)}/${id.take(6)}/$id/${id}_large_vertical.jpg"
    }
}

@Serializable
class DetailsResponse(
    val title: Title,
    val episodes: List<Episode>,
)

@Serializable
class Title(
    private val name: String,
    private val summary: String?,
    @SerialName("vertical_thumbnail_url") private val thumbnailUrl: String?,
    private val artists: List<NamedEntity>?,
    private val genres: List<NamedEntity>?,
    @SerialName("sub_genres") private val subGenres: List<NamedEntity>?,
    private val tags: List<NamedEntity>?,
    @SerialName("is_completed") private val isCompleted: Boolean?,
    @SerialName("is_mature") private val isMature: Boolean?,
    private val agency: String?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        artist = artists?.joinToString { it.name }
        description = buildString {
            summary?.let { append(it) }
            agency?.let { append("\n\nPublisher: $it") }
            if (isMature == true) append("\n\nRating: 18+")
        }
        genre = listOfNotNull(genres, subGenres, tags)
            .flatten()
            .map(NamedEntity::name)
            .distinct()
            .joinToString()
        status = if (isCompleted == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = thumbnailUrl?.replace("_vertical.jpg", "_large_vertical.jpg")
    }
}

@Serializable
class NamedEntity(
    val name: String,
)

@Serializable
class Episode(
    private val id: Int,
    private val number: String,
    @SerialName("start_at") private val startAt: String?,
    @SerialName("is_free") private val isFree: Boolean?,
    @SerialName("was_purchased") private val wasPurchased: Boolean?,
    @SerialName("display_order") private val displayOrder: Int?,
) {
    val isLocked: Boolean
        get() = isFree == false && wasPurchased == false

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = id.toString()
        name = lock + "Chapter $number"
        date_upload = dateFormat.tryParse(startAt)
        chapter_number = displayOrder?.toFloat() ?: -1f
    }
}

private val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.ROOT)

@Serializable
class ViewerResponse(
    @SerialName("image_data") val imageData: List<ImageData>?,
)

@Serializable
class ImageData(
    val num: Int,
    val path: String,
)

@Serializable
class PageResponse(
    @SerialName("encrypted_image") val encryptedImage: String,
    val iv: String,
    val salt: String,
    val iterations: Int,
    @SerialName("kms_encrypted_data_key") val kmsEncryptedDataKey: String,
    @SerialName("file_name") val fileName: String,
)

@Suppress("unused")
@Serializable
class KeyRequestBody(
    @SerialName("encrypted_key") val encryptedKey: String,
    @SerialName("file_name") val fileName: String,
)

@Serializable
class KeyResponse(
    @SerialName("decrypted_key") val decryptedKey: String,
)
