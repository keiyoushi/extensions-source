package eu.kanade.tachiyomi.extension.es.insanosscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class Series(
    private val id: Int,
    private val title: String,
    private val description: String,
    @SerialName("cover_image")
    private val coverImage: String,
    private val genre: String?,
    @SerialName("series_type")
    private val type: String,
    private val status: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = this@Series.title
        description = this@Series.description
        thumbnail_url = if (coverImage.startsWith("/")) {
            baseUrl + coverImage
        } else {
            coverImage
        }
        genre = buildList {
            add(type)
            this@Series.genre?.split(",")?.let(::addAll)
        }.joinToString()
        status = when (this@Series.status.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class Chapter(
    private val id: Long,
    @SerialName("series_id")
    private val seriesId: Int,
    @SerialName("chapter_number")
    private val chapterNumber: Int,
    @SerialName("is_unlocked")
    private val isUnlocked: Boolean,
    @SerialName("created_at")
    private val createdAt: String?,
) {
    fun toSChapter(showPaid: Boolean): SChapter? {
        if (!isUnlocked && !showPaid) return null

        return SChapter.create().apply {
            url = "$seriesId/$id"
            name = buildString {
                append("Capítulo $chapterNumber")
                if (!isUnlocked) append(" 🔒")
            }
            chapter_number = chapterNumber.toFloat()
            date_upload = Instant.tryParse("${createdAt?.trimEnd('Z')}Z")
        }
    }
}

@Serializable
class Pages(
    val pages: List<String>,
)
