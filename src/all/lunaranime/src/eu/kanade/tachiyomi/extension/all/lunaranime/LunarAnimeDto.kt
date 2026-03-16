package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class LunarSearchResponse(
    val manga: List<LunarMangaDto> = emptyList(),
    val page: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
)

@Serializable
class LunarMangaResponse(
    val manga: LunarMangaDto,
)

@Serializable
class LunarMangaDto(
    val slug: String,
    val title: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val genres: String? = null,
    @SerialName("publication_status") val publicationStatus: String? = null,
    val author: String? = null,
    val artist: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@LunarMangaDto.title
        thumbnail_url = coverUrl
        url = "/manga/$slug"
        author = this@LunarMangaDto.author?.trim()
        artist = this@LunarMangaDto.artist?.trim()
        description = this@LunarMangaDto.description
        status = when (publicationStatus?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "upcoming" -> SManga.ONGOING
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = genres?.let { g ->
            try {
                g.parseAs<List<String>>().joinToString()
            } catch (e: Exception) {
                g
            }
        }
        initialized = true
    }
}

@Serializable
class LunarChapterListResponse(
    val data: List<LunarChapterDto> = emptyList(),
)

@Serializable
class LunarChapterDto(
    val chapter: String,
    @SerialName("chapter_number") val chapterNumber: Float,
    @SerialName("chapter_subnumber") val chapterSubnumber: Float? = null,
    @SerialName("chapter_title") val chapterTitle: String? = null,
    val language: String,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String, isLocked: Boolean): SChapter = SChapter.create().apply {
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
        chapter_number = chapter.toFloatOrNull() ?: this@LunarChapterDto.chapterNumber
        date_upload = uploadedAt?.let { parseChapterDate(it) } ?: 0L
        scanlator = language.uppercase(Locale.ROOT)
    }

    private fun parseChapterDate(date: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.tryParse(date)
    } catch (e: Exception) {
        0L
    }
}

@Serializable
class LunarPageListResponse(
    val data: LunarPageListData? = null,
)

@Serializable
class LunarPageListData(
    val images: List<String> = emptyList(),
    @SerialName("session_data") val sessionData: String? = null,
)

@Serializable
class LunarPageListDecrypted(
    val data: LunarPageListData,
)

@Serializable
class SecretKeyDto(
    val secretKey: String,
)

@Serializable
class LunarRecentResponse(
    @SerialName("our_mangas") val mangas: List<LunarMangaDto> = emptyList(),
    val page: Int = 0,
    val limit: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable
class LunarPasswordInfoResponse(
    @SerialName("chapter_passwords") val chapterPasswords: List<LunarChapterPasswordDto> = emptyList(),
    @SerialName("has_series_password") val hasSeriesPassword: Boolean = false,
)

@Serializable
class LunarChapterPasswordDto(
    @SerialName("chapter_number") val chapterNumber: String? = null,
    val language: String? = null,
)
