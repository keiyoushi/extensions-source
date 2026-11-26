package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Instant

@Serializable
class LunarSearchResponse(
    val manga: List<LunarMangaDto>,
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class LunarMangaResponse(
    val manga: LunarMangaDto,
)

@Serializable
class LunarMangaDto(
    val slug: String,
    val title: String,
    val description: String?,
    @SerialName("cover_url") val coverUrl: String?,
    val genres: String?,
    @SerialName("publication_status") val publicationStatus: String?,
    val author: String?,
    val artist: String?,
    @SerialName("alternative_titles") val alternativeTitles: String?,
    val demographic: String?,
    val themes: String?,
    private val rating: String?,
    @SerialName("publication_year") private val publicationYear: Int?,
    private val publisher: String?,
    private val serialization: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@LunarMangaDto.title
        thumbnail_url = coverUrl
        url = "/manga/$slug"
        author = this@LunarMangaDto.author?.trim()
        artist = this@LunarMangaDto.artist?.trim()

        description = MarkdownDescription.Builder()
            .info(rating, publicationYear?.toString(), publisher, serialization.takeIf { it != publisher })
            .description(this@LunarMangaDto.description)
            .altTitles(alternativeTitles?.parseAs<List<String>>().orEmpty())
            .build()

        status = when (publicationStatus?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        genre = buildList {
            demographic?.takeIf { it.isNotBlank() }?.let { d ->
                add(d.replaceFirstChar { it.titlecase(Locale.ROOT) })
            }

            genres?.let { addAll(it.parseAs<List<String>>()) }
            themes?.let { addAll(it.parseAs<List<String>>()) }
        }.filter { it.isNotBlank() }.distinct().joinToString().ifEmpty { null }
    }
}

@Serializable
class LunarChapterListResponse(
    val data: List<LunarChapterDto>,
)

@Serializable
class LunarChapterDto(
    val chapter: String,
    @SerialName("chapter_number") val chapterNumber: Float?,
    @SerialName("chapter_title") val chapterTitle: String?,
    val language: String,
    @SerialName("uploaded_at") val uploadedAt: String?,
    @SerialName("uploader_profile") val uploaderProfile: LunarUploaderDto?,
) {
    fun toSChapter(mangaSlug: String, isLocked: Boolean, showLanguage: Boolean): SChapter = SChapter.create().apply {
        url = "/manga/$mangaSlug/$chapter?lang=$language"
        val prefix = if (isLocked) "🔒 " else ""
        val chapterName = chapter.removeSuffix(".00").removeSuffix(".0")
        val chapterNum = "Chapter $chapterName"
        name = prefix + if (chapterTitle.isNullOrBlank()) {
            chapterNum
        } else if (chapterTitle.contains(chapterNum, ignoreCase = true) ||
            chapterTitle.contains("Ch.$chapterName", ignoreCase = true) ||
            chapterTitle.contains("Volume", ignoreCase = true) ||
            chapterTitle.contains("Vol.", ignoreCase = true)
        ) {
            chapterTitle
        } else {
            "$chapterNum: $chapterTitle"
        }
        (chapter.toFloatOrNull() ?: this@LunarChapterDto.chapterNumber)?.let { chapter_number = it }
        date_upload = Instant.tryParse(uploadedAt)
        val languageCode = language.uppercase(Locale.ROOT)
        val uploader = uploaderProfile?.username?.takeIf { it.isNotBlank() }
        scanlator = when {
            uploader == null -> languageCode
            showLanguage -> "$uploader ($languageCode)"
            else -> uploader
        }
    }
}

@Serializable
class LunarReaderState(
    val pages: List<LunarReaderPage>? = null,
    val price: Int? = null,
    val balance: Int? = null,
    val reason: String? = null,
    val message: String? = null,
    val check: Boolean = false,
    val turnstile: Boolean = false,
    val area: String? = null,
)

@Serializable
class LunarAdPoints(val points: Int)

@Serializable
class LunarBuyResponse(val success: Boolean = false, val error: String? = null)

@Serializable
class LunarBuyBody(
    @SerialName("content_type") val contentType: String,
    val slug: String,
    val chapter: String,
    val language: String,
)

@Serializable
class LunarHeartbeatBody(val beat: String)

@Serializable
class LunarReaderPage(
    val url: String,
    val veil: String?,
)

@Serializable
class LunarRecentResponse(
    @SerialName("our_mangas") val mangas: List<LunarMangaDto>,
    val page: Int,
    val limit: Int,
    @SerialName("total_count") val totalCount: Int,
)

@Serializable
class LunarPasswordInfoResponse(
    @SerialName("chapter_passwords") val chapterPasswords: List<LunarChapterPasswordDto>,
    @SerialName("has_series_password") val hasSeriesPassword: Boolean,
)

@Serializable
class LunarChapterPasswordDto(
    @SerialName("chapter_number") val chapterNumber: String?,
    val language: String?,
)

@Serializable
class LunarFacetsDto(
    val tags: List<LunarFacetDto>,
)

@Serializable
class LunarFacetDto(
    val name: String,
)

@Serializable
class LunarUploaderDto(
    val username: String?,
)

@Serializable
class LunarSimilarResponse(
    val data: List<LunarSimilarDto>,
)

@Serializable
class LunarSimilarDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = "/manga/$slug"
        title = this@LunarSimilarDto.title
        thumbnail_url = coverUrl
    }
}
