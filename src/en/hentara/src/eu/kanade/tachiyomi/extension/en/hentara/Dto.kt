package eu.kanade.tachiyomi.extension.en.hentara

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class HentaraIndexDto(
    val comics: List<HentaraComicDto> = emptyList(),
)

@Serializable
class HentaraComicDto(
    val title: String,
    private val slug: String,
    @SerialName("thumbnail_url") private val thumbnailUrl: String? = null,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("latest_episode_date") val latestEpisodeDate: String? = null,
    val genres: List<HentaraGenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manhwa/$slug"
        title = this@HentaraComicDto.title
        thumbnail_url = thumbnailUrl
        genre = genres.joinToString { it.name }
    }
}

@Serializable
class HentaraGenreDto(
    val name: String,
)

@Serializable
class HentaraMangaDto(
    val comic: HentaraComicFullDto,
    val episodes: List<HentaraEpisodeShortDto> = emptyList(),
)

@Serializable
class HentaraComicFullDto(
    private val title: String,
    val slug: String,
    private val description: String? = null,
    @SerialName("thumbnail_url") private val thumbnailUrl: String? = null,
    private val genres: List<HentaraGenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manhwa/$slug"
        title = this@HentaraComicFullDto.title
        thumbnail_url = thumbnailUrl
        description = this@HentaraComicFullDto.description
        genre = genres.joinToString { it.name }
    }
}

@Serializable
class HentaraEpisodeShortDto(
    @SerialName("episode_number") val episodeNumber: Int,
    private val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun chapterName() = buildString {
        append("Chapter $episodeNumber")
        if (!title.isNullOrBlank()) append(" - $title")
    }
}

@Serializable
class HentaraEpisodeDto(
    val pages: List<HentaraPageDto> = emptyList(),
)

@Serializable
class HentaraPageDto(
    @SerialName("page_number") val pageNumber: Int,
    @SerialName("image_url") val imageUrl: String,
)
