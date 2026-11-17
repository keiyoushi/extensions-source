package eu.kanade.tachiyomi.extension.th.toonjai

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class ItemsDto(val items: List<ContentDto>)

@Serializable
class ContentDto(
    private val name: String,
    private val slug: String,
    private val description: String? = null,
    private val ageRating: String? = null,
    private val thumbnailImage: String? = null,
    private val ratingValue: Double = 0.0,
    private val ratingTotal: Int = 0,
    private val completionStatus: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    private val episodes: List<EpisodeDto> = emptyList(),
) {
    // R and NC episodes only open after viewing an ad, one by one
    private val isLocked get() = ageRating == "R" || ageRating == "NC"

    fun toSManga() = SManga.create().apply {
        url = "/content/$slug"
        title = name
        thumbnail_url = thumbnailImage
    }

    fun toSMangaDetails() = toSManga().apply {
        genre = genres.map { it.label }.ifEmpty { null }?.joinToString()
        status = when (completionStatus) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(if (ratingTotal > 0) ratingValue / ratingTotal else null, max = 5, votes = ratingTotal)
            .description(this@ContentDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .build()
    }

    fun toSChapters() = episodes.sortedByDescending { it.no }.map { it.toSChapter(slug, isLocked) }
}

@Serializable
class GenreDto(private val name: String) {
    // Names are "English - ไทย", the site shows the Thai part
    val label get() = name.substringAfter(" - ")
}

@Serializable
class EpisodeDto(
    val no: Double,
    private val name: String? = null,
    private val publishedAt: String? = null,
) {
    fun toSChapter(slug: String, locked: Boolean) = SChapter.create().apply {
        val number = no.toString().removeSuffix(".0")
        val label = "ตอนที่ $number"
        url = "/content/$slug/episode/$number"
        name = listOfNotNull(label, this@EpisodeDto.name?.takeIf { it.isNotBlank() && it != label })
            .joinToString(" ")
            .let { if (locked) "🔒 $it" else it }
        chapter_number = no.toFloat()
        date_upload = Instant.tryParse(publishedAt)
    }
}

@Serializable
class ReaderDto(val images: List<String>, val isRestricted: Boolean)

@Serializable
class NavLinkDto(private val href: String)
