package eu.kanade.tachiyomi.extension.ar.despairmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class WorkDto(
    val id: String,
    private val slug: String,
    @SerialName("title_ar") private val titleAr: String?,
    @SerialName("title_en") private val titleEn: String?,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val genres: List<String>? = null,
    private val synopsis: String? = null,
    private val status: String? = null,
    @SerialName("author_or_studio") private val authorOrStudio: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        val mainTitle = titleAr?.takeIf { it.isNotBlank() } ?: titleEn!!
        url = slug
        title = mainTitle
        thumbnail_url = coverUrl
        author = authorOrStudio?.takeIf { it.isNotBlank() }
        genre = genres?.joinToString()
        description = buildString {
            synopsis?.let { append(it) }
            titleEn?.takeIf { it.isNotBlank() && it != mainTitle }?.let {
                if (isNotEmpty()) append("\n\n")
                append("أسماء أخرى:\n", it)
            }
        }
        status = when (this@WorkDto.status) {
            "مستمر" -> SManga.ONGOING
            "مكتمل" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        memo = buildJsonObject { put(DespairManga.WORK_ID, id) }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("chapter_number") private val chapterNumber: Double,
    private val title: String? = null,
    @SerialName("is_free") private val isFree: Boolean? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        val chapterName = title?.takeIf { it.isNotBlank() } ?: "الفصل $number"
        url = "/series/$slug/main/ch/$number"
        name = if (isFree == false) "🔒 $chapterName" else chapterName
        chapter_number = chapterNumber.toFloat()
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject { put(DespairManga.CHAPTER_ID, id) }
    }
}

@Serializable
class PagesDto(
    val pages: List<String>? = null,
)
