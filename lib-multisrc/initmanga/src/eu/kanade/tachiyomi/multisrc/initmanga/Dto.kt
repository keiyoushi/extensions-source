package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Serializable
class SearchDto(
    private val title: String,
    private val url: String,
    private val thumb: String? = null,
    @SerialName("post_type") val postType: String,
) {
    fun toSManga() = SManga.create().apply {
        title = Jsoup.parseBodyFragment(this@SearchDto.title).text()
        url = this@SearchDto.url.toHttpUrl().encodedPath
        thumbnail_url = thumb
    }
}

@Serializable
class EncryptedPayloadDto(
    val ciphertext: String,
    val iv: String,
    val salt: String? = null,
    val cid: Long? = null,
    val e: Long? = null,
    val g: String? = null,
)

@Serializable
class InitMangaDataDto(
    val restUrl: String,
    val nonce: String? = null,
    @SerialName("decryption_key") val decryptionKey: String? = null,
)

@Serializable
class ChapterKeyRequestDto(
    @SerialName("chapter_id") private val chapterId: Long,
    private val epoch: Long,
    private val grant: String,
)

@Serializable
class ChapterKeyResponseDto(
    val key: String,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ChapterDto(
    val title: String? = null,
    val number: Float,
    val slug: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("lock_type") val lockType: String,
    @SerialName("is_purchased") val isPurchased: Boolean,
)

@Serializable
class GenreData(
    val name: String,
    val path: String,
)
