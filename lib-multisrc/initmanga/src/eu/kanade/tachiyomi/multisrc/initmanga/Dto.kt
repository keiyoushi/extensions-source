package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

@Serializable
class Dto(
    private val title: String? = null,
    private val url: String? = null,
    private val link: String? = null,
    private val thumb: String? = null,
    private val image: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@Dto.title?.let { Jsoup.parseBodyFragment(it).text() }.orEmpty()
        val fullUrl = this@Dto.url ?: this@Dto.link.orEmpty()

        val urlPath = try {
            fullUrl.toHttpUrlOrNull()?.encodedPath ?: fullUrl
        } catch (_: Exception) {
            fullUrl
        }
        this.url = urlPath
        thumbnail_url = thumb ?: image
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
    val restUrl: String? = null,
    val nonce: String? = null,
)

@Serializable
class ChapterKeyRequestDto(
    @SerialName("chapter_id") private val chapterId: Long,
    private val epoch: Long,
    private val grant: String,
    private val verdict: String = "pass",
    private val score: Int = 0,
    private val codes: List<String> = emptyList(),
    @SerialName("challenge_token") private val challengeToken: String = "",
)

@Serializable
class ChapterKeyResponseDto(
    val key: String,
)

@Serializable
class MangaIdDto(
    val id: Int,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class ChapterDto(
    val title: String,
    val number: Float,
    val slug: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("lock_type") val lockType: String = "none",
    @SerialName("is_purchased") val isPurchased: Boolean = false,
)
