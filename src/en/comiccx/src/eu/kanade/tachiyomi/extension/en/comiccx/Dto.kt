package eu.kanade.tachiyomi.extension.en.comiccx

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.get
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val manga: List<MangaItem> = emptyList(),
    val pagination: Pagination? = null,
)

@Serializable
class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
)

@Serializable
class MangaItem(
    val id: Int,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val genres: List<String>? = null,
    val slug: String,
    @SerialName("required_tier") val requiredTier: String? = null,
    val tier: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = slug
        title = this@MangaItem.title
        thumbnail_url = coverImage.resolveCoverUrl(baseUrl)
        author = this@MangaItem.author
        artist = this@MangaItem.artist
        genre = genres?.joinToString(", ")
        status = when (this@MangaItem.status?.lowercase(Locale.ENGLISH)) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            if (!this@MangaItem.description.isNullOrBlank()) {
                append(this@MangaItem.description)
            }
            val effectiveTier = requiredTier ?: tier
            if (!effectiveTier.isNullOrBlank() && effectiveTier != "free" && effectiveTier != "tier_0") {
                if (isNotEmpty()) append("\n\n")
                append("⚠ This title requires ${effectiveTier.replace("_", " ").uppercase()} access. Log in via WebView to read.")
            }
        }
    }
}

@Serializable
class ChapterItem(
    val id: Int,
    @SerialName("chapter_number") val chapterNumber: Float,
    val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val pages: List<String> = emptyList(),
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$id"
        val chapNumStr = if (chapterNumber % 1 == 0f) chapterNumber.toInt().toString() else chapterNumber.toString()
        name = buildString {
            append("Chapter $chapNumStr")
            if (!title.isNullOrBlank()) append(" - $title")
        }
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(createdAt)
    }
}

private fun String?.resolveCoverUrl(baseUrl: String): String? {
    if (this.isNullOrBlank()) return null
    return when {
        startsWith("data:") -> "https://127.0.0.1/?" + this.substringAfter(":")
        startsWith("/") -> "$baseUrl$this"
        else -> this
    }
}
