package eu.kanade.tachiyomi.extension.vi.cuutruyen

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val data: List<MangaListItem>,
    @SerialName("_metadata") val metadata: PaginationMetadata,
)

@Serializable
class PaginationMetadata(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaListItem(
    private val id: Int,
    private val name: String,
    @SerialName("cover_url") private val coverUrl: String,
    @SerialName("cover_mobile_url") private val coverMobileUrl: String? = null,
) {
    fun toSManga(useMobileCover: Boolean): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = (if (useMobileCover) coverMobileUrl ?: coverUrl else coverUrl).normalizeStorageUrl()
    }
}

@Serializable
class MangaDetailResponse(val data: MangaDetailDto)

@Serializable
class MangaDetailDto(
    private val id: Int,
    private val name: String,
    @SerialName("cover_url") private val coverUrl: String,
    @SerialName("cover_mobile_url") private val coverMobileUrl: String? = null,
    private val author: AuthorDto? = null,
    @SerialName("full_description") private val fullDescription: String? = null,
    private val tags: List<MangaTagDto>,
) {
    fun toSManga(useMobileCover: Boolean): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = (if (useMobileCover) coverMobileUrl ?: coverUrl else coverUrl).normalizeStorageUrl()
        author = this@MangaDetailDto.author?.name
        genre = tags.joinToString { it.name }
        description = fullDescription.toPlainText()
        status = tags.toStatus()
    }

    private fun List<MangaTagDto>.toStatus(): Int {
        val statusTags = joinToString(" ") { it.name }.lowercase(Locale.ROOT)
        return when {
            "tạm ngưng" in statusTags -> SManga.ON_HIATUS
            "hoàn thành" in statusTags -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
    }
}

@Serializable
class AuthorDto(val name: String)

@Serializable
class MangaTagDto(val name: String)

@Serializable
class ChapterListResponse(val data: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: String,
    private val name: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        url = id.toString()
        memo = buildJsonObject { put("mangaId", JsonPrimitive(mangaId)) }
        name = buildString {
            append("Chương ")
            append(number)
            this@ChapterDto.name?.takeIf { it.isNotEmpty() }?.let {
                append(' ')
                append(it)
            }
        }
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterReaderResponse(val data: ChapterReaderDto)

@Serializable
class ChapterReaderDto(val pages: List<ChapterPageDto>)

@Serializable
class ChapterPageDto(
    val order: Int,
    @SerialName("image_url") private val imageUrl: String,
    @SerialName("drm_data") private val drmData: String? = null,
) {
    fun imageUrlWithDrm(): String = imageUrl.normalizeStorageUrl().toHttpUrl().newBuilder()
        .apply {
            drmData?.takeIf(String::isNotBlank)?.let {
                fragment("drm_data=$it")
            }
        }
        .build()
        .toString()
}

@Serializable
class TagResponse(val data: TagGroupsDto)

@Serializable
class TagGroupsDto(
    @SerialName("common_tags") private val commonTags: List<TagDto>,
    @SerialName("warning_tags") private val warningTags: List<TagDto>,
    @SerialName("normal_tags") private val normalTags: List<TagDto>,
) {
    fun allTags(): List<TagOption> = (commonTags + warningTags + normalTags)
        .distinctBy { it.slug }
        .map { TagOption(it.name, it.slug) }
}

@Serializable
class TagDto(
    val id: Int,
    val name: String,
    val slug: String,
)

internal fun String?.toPlainText(): String? = this
    ?.let(Jsoup::parseBodyFragment)
    ?.wholeText()
    ?.takeIf { it.isNotEmpty() }

// Website redirect there domain cdn in javascript
private fun String.normalizeStorageUrl(): String {
    val url = toHttpUrl()
    val replacementHost = when (url.host) {
        "storage-ct.lrclib.net" -> "storage-bravo.cuutruyen.net"
        "storage-ct-riften.site" -> "storage-charlie.cuutruyen.net"
        else -> return this
    }

    return url.newBuilder()
        .host(replacementHost)
        .build()
        .toString()
}
