package eu.kanade.tachiyomi.extension.tr.faichisubs

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SeriesDto(
    private val id: Int,
    private val apiId: Int? = null,
    private val title: String,
    private val titleTr: String? = null,
    private val displayTitle: String? = null,
    private val slug: String,
    private val coverImage: String? = null,
    private val type: String,
    private val synonyms: List<String> = emptyList(),
    val viewCount: Int = 0,
    val episodes: List<EpisodeNumberDto> = emptyList(),
) {
    val isManga get() = type == "MANGA"

    // The site's addresses end with the series' AniList id, or its own id when it has none
    val key get() = (apiId ?: id).toString()

    fun matches(query: String) = (listOfNotNull(title, titleTr) + synonyms).any { it.contains(query, ignoreCase = true) }

    fun toSManga() = SManga.create().apply {
        url = "/detay/$slug-$key"
        title = shownTitle(displayTitle, this@SeriesDto.title, titleTr)
        thumbnail_url = coverImage
    }
}

@Serializable
class EpisodeNumberDto(
    private val episodeNum: Double,
) {
    val number get() = episodeNum.toString().removeSuffix(".0")
}

@Serializable
class ChapterDataDto(
    val mangaPages: List<PageDto> = emptyList(),
    val series: SeriesDetailsDto,
)

@Serializable
class PageDto(
    val imageUrl: String,
    val pageOrder: Int,
)

@Serializable
class SeriesDetailsDto(
    private val id: Int,
    private val apiId: Int? = null,
    private val title: String,
    private val titleTr: String? = null,
    private val displayTitle: String? = null,
    private val slug: String,
    private val description: String? = null,
    private val coverImage: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val synonyms: List<String> = emptyList(),
    private val score: Int? = null,
    private val episodes: List<EpisodeDto> = emptyList(),
) {
    private val pathKey get() = "$slug-${apiId ?: id}"

    fun toSManga() = SManga.create().apply {
        val shown = shownTitle(displayTitle, this@SeriesDetailsDto.title, titleTr)
        url = "/detay/$pathKey"
        title = shown
        thumbnail_url = coverImage
        genre = genres.joinToString().ifEmpty { null }
        status = when (this@SeriesDetailsDto.status) {
            "RELEASING" -> SManga.ONGOING
            "FINISHED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(score, max = 100)
            .description(this@SeriesDetailsDto.description)
            .altTitles((listOfNotNull(this@SeriesDetailsDto.title, titleTr) + synonyms).filter { it != shown })
            .build()
    }

    fun toSChapters() = episodes.reversed().map { it.toSChapter(pathKey) }
}

@Serializable
class EpisodeDto(
    private val episodeNum: Double,
    private val title: String? = null,
    private val translator: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(pathKey: String) = SChapter.create().apply {
        val number = episodeNum.toString().removeSuffix(".0")
        url = "/oku/$pathKey/$number"
        name = title ?: "Bölüm $number"
        chapter_number = episodeNum.toFloat()
        scanlator = translator
        date_upload = Instant.tryParse(createdAt)
    }
}

// The site shows the Turkish title only for series set that way
private fun shownTitle(displayTitle: String?, title: String, titleTr: String?) = if (displayTitle == "TURKISH" && titleTr != null) titleTr else title
