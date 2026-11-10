package eu.kanade.tachiyomi.extension.en.toonhub

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ComicListDto(
    val data: List<ComicDto>,
)

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    private val description: String? = null,
    private val status: String? = null,
    private val ageRating: Int? = null,
    private val isComicPurchased: Boolean = false,
    private val genres: List<NameDto> = emptyList(),
    private val tags: List<NameDto> = emptyList(),
    private val images: List<ImageDto> = emptyList(),
    private val episodes: List<EpisodeDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@ComicDto.title.trim()
        thumbnail_url = images.firstOrNull { it.kind == "thumbnail" && it.size == "original" }?.url
        genre = genres.joinToString { it.name }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(this@ComicDto.description)
            .field("Tags", tags.map { it.name.replace('_', ' ') })
            .field("Age rating", ageRating?.let { "$it+" })
            .build()
        status = when (this@ComicDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Only the free episodes, and the ones the reader has bought, can be read without VIP
    fun toSChapters(hideLocked: Boolean) = episodes
        .filterNot { it.comingSoon }
        .mapNotNull { episode ->
            val locked = episode.accessType != "free" && !episode.isPurchased && !isComicPurchased
            if (locked && hideLocked) null else episode.toSChapter(slug, locked)
        }
        .reversed()
}

@Serializable
class EpisodeDto(
    private val slug: String,
    private val title: String,
    private val episodeName: String? = null,
    private val episodeNumber: Float? = null,
    val accessType: String,
    val isPurchased: Boolean = false,
    val comingSoon: Boolean = false,
    private val scheduledPublishAt: String? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(comicSlug: String, locked: Boolean) = SChapter.create().apply {
        url = "/comics/$comicSlug/$slug/"
        val fullName = listOfNotNull(title, episodeName?.takeIf { it.isNotBlank() }).joinToString(": ")
        name = if (locked) "🔒 $fullName" else fullName
        chapter_number = episodeNumber ?: -1f
        date_upload = Instant.tryParse(scheduledPublishAt ?: createdAt)
    }
}

@Serializable
class EpisodePagesDto(
    val panels: List<PanelDto>,
)

@Serializable
class PanelDto(
    val index: Int,
    val url: String,
)

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class ImageDto(
    val kind: String,
    val size: String,
    val url: String,
)
