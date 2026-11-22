package eu.kanade.tachiyomi.extension.en.fanon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class ConvexRequest<T>(
    private val path: String,
    private val args: T,
    private val format: String,
)

@Serializable
class ConvexResponse<T>(
    val status: String,
    val value: T?,
)

@Serializable
class ListArgs(
    private val numItems: Int,
    private val sortBy: String,
    private val fanficFilter: String? = null,
    private val fandomIds: List<String>? = null,
    private val startIndexKey: JsonElement? = null,
)

@Serializable
object EmptyArgs

@Serializable
class SearchArgs(private val searchText: String)

@Serializable
class FanficListDto(
    val fanfics: List<FanficCardDto>,
    val hasMore: Boolean,
    val nextStartIndexKey: JsonElement?,
)

@Serializable
class FanficCardDto(
    private val fanficSlug: String,
    private val name: String,
    val medium: String,
    private val updatedAt: Double?,
    private val description: String?,
    private val whatIf: String?,
    private val containsAi: Boolean = false,
    private val creatorCredits: List<CreatorDto> = emptyList(),
    private val fandoms: List<NameDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        setFanfic(fanficSlug, name, updatedAt, creatorCredits, fandoms, containsAi)
        description = MarkdownDescription.Builder()
            .description(this@FanficCardDto.description)
            .field("What if", whatIf)
            .build()
    }
}

@Serializable
class SearchResultsDto(val fanfics: List<SearchResultDto>)

@Serializable
class SearchResultDto(
    private val fanficSlug: String,
    private val name: String,
    val medium: String,
    private val version: Double?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/fanfics/$fanficSlug"
        title = name
        thumbnail_url = thumbnailUrl(fanficSlug, version)
    }
}

@Serializable
class FandomDto(
    val fandomId: String,
    val name: String,
)

@Serializable
class FanficPageDto(
    val fanfic: FanficDto,
    val episodes: List<EpisodeDto>,
    private val followers: Int?,
) {
    // Video series have no pages to read
    val comicEpisodes get() = if (fanfic.medium == "FANCOMIC") episodes else emptyList()

    fun toSManga() = fanfic.toSManga(comicEpisodes.size, comicEpisodes.sumOf { it.views ?: 0.0 }.toLong(), followers)
}

@Serializable
class FanficDto(
    val fanficSlug: String,
    private val name: String,
    val medium: String,
    private val description: String?,
    private val whatIf: String?,
    private val adaptedFrom: String?,
    private val status: String?,
    private val updatedAt: Double?,
    private val containsAi: Boolean = false,
    private val creatorCredits: List<CreatorDto> = emptyList(),
    private val fandoms: List<NameDto> = emptyList(),
    private val tags: List<NameDto> = emptyList(),
) {
    fun toSManga(episodes: Int, views: Long, followers: Int?) = SManga.create().apply {
        setFanfic(fanficSlug, name, updatedAt, creatorCredits, fandoms + tags, containsAi)
        description = MarkdownDescription.Builder()
            .info("$episodes episodes", views.takeIf { it > 0 }?.let { "$it views" }, followers?.let { "$it followers" })
            .description(this@FanficDto.description)
            .field("What if", whatIf)
            .links(listOf("Adapted from" to adaptedFrom))
            .build()
        status = when (this@FanficDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "ON HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

private fun SManga.setFanfic(
    fanficSlug: String,
    name: String,
    version: Double?,
    credits: List<CreatorDto>,
    tags: List<NameDto>,
    containsAi: Boolean,
) {
    url = "/fanfics/$fanficSlug"
    title = name
    thumbnail_url = thumbnailUrl(fanficSlug, version)
    author = credits.filter { "AUTHOR" in it.roles }.joinToString { it.nickname }.ifEmpty { null }
    artist = credits.filter { "ARTIST" in it.roles }.joinToString { it.nickname }.ifEmpty { null }
    genre = (tags.map { it.name.trim() } + listOfNotNull("AI-generated".takeIf { containsAi }))
        .distinctBy { it.lowercase() }
        .joinToString()
        .ifEmpty { null }
}

@Serializable
class CreatorDto(
    val nickname: String,
    val roles: List<String> = emptyList(),
)

@Serializable
class NameDto(val name: String)

@Serializable
class EpisodeDto(
    val name: String,
    val number: Int,
    private val title: String?,
    private val lastReleasedAt: Double?,
    val views: Double?,
    val fancomic: FancomicDto?,
) {
    fun toSChapter(fanficSlug: String) = SChapter.create().apply {
        url = "/fanfics/$fanficSlug/episodes/${this@EpisodeDto.name}"
        val label = "Episode $number"
        val title = title?.trim().orEmpty()
        name = when {
            title.startsWith(label, ignoreCase = true) && title.getOrNull(label.length)?.isDigit() != true -> title
            title.isEmpty() -> label
            else -> "$label: $title"
        }
        chapter_number = number.toFloat()
        date_upload = lastReleasedAt?.toLong() ?: 0L
    }
}

@Serializable
class FancomicDto(
    val pageCount: Int,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(val updatedAt: Double?)

@Serializable
class EpisodeAuthRequest(
    private val fanficSlug: String,
    private val episodeNumbers: List<Int>,
)

private fun thumbnailUrl(fanficSlug: String, version: Double?) = "$MEDIA_URL/v_${version?.toLong() ?: 1}/w_1200/f_auto/c_fill/src/Fanfics/$fanficSlug/Thumbnail"

const val MEDIA_URL = "https://media.aws.fanon.co/images/view"
