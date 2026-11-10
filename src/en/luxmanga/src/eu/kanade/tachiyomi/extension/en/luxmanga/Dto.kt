package eu.kanade.tachiyomi.extension.en.luxmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class WorkList(
    val data: List<Work>,
    val meta: Meta,
)

@Serializable
class RankingList(
    val data: List<Work>,
)

@Serializable
class Meta(
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class WorkId(
    @SerialName("work_id") val workId: String,
)

@Serializable
class Work(
    private val id: String,
    private val title: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val author: String? = null,
    private val status: String? = null,
) {
    fun toSManga(apiUrl: HttpUrl, metadata: Metadata? = null, genres: List<Genre> = emptyList()) = SManga.create().apply {
        url = id
        title = this@Work.title
        thumbnail_url = coverUrl?.takeIf { it.isNotEmpty() }?.let { apiUrl.resolve(it)?.toString() }
        author = this@Work.author?.takeIf { it.isNotBlank() && it != "n/a" }?.replace(" / ", ", ")
        status = when (this@Work.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = (genres.map { it.nameEn } + metadata?.tags.orEmpty())
            .map { tag -> tag.replaceFirstChar { it.uppercase() } }
            .distinctBy { it.lowercase() }
            .joinToString()
            .takeIf { it.isNotEmpty() }

        val altTitles = metadata?.alternativeTitles.orEmpty().filterNot { it.equals(this@Work.title, ignoreCase = true) }
        description = buildString {
            this@Work.description?.takeIf { it.isNotBlank() }?.let(::append)
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("**Also known as:**\n")
                altTitles.joinTo(this, "\n") { "- $it" }
            }
        }.takeIf { it.isNotEmpty() }
    }
}

@Serializable
class Metadata(
    @SerialName("alternative_titles") val alternativeTitles: List<String>,
    val tags: List<String>,
)

@Serializable
class GenreList(
    val data: List<Genre>,
)

@Serializable
class Genre(
    val slug: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("work_count") val workCount: Int = 0,
)

@Serializable
class ChapterList(
    val data: List<Chapter>,
    val meta: Meta,
)

@Serializable
class Chapter(
    private val id: String,
    @SerialName("chapter_number") private val chapterNumber: Float,
    private val title: String? = null,
    val coins: Int,
    @SerialName("created_at") private val createdAt: String? = null,
    val images: List<String> = emptyList(),
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(createdAt)
        val number = chapterNumber.toString().removeSuffix(".0")
        name = buildString {
            if (coins > 0) append("🔒 ")
            append("Chapter ", number)
            if (!title.isNullOrBlank() && title.trim().lowercase() !in setOf(number, "chapter $number")) {
                append(" — ", title.trim())
            }
        }
    }
}
