package eu.kanade.tachiyomi.extension.en.hentara.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class HentaraIndexDto(
    val comics: List<HentaraComicDto>,
)

@Serializable
data class HentaraComicDto(
    val title: String,
    val slug: String,
    val thumbnail_url: String,
    val view_count: Int = 0,
    val latest_episode_date: String? = null,
    val genres: List<HentaraGenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/manhwa/$slug"
        title = this@HentaraComicDto.title
        thumbnail_url = this@HentaraComicDto.thumbnail_url
        genre = genres.joinToString { it.name }
    }
}

@Serializable
data class HentaraGenreDto(
    val name: String,
)

@Serializable
data class HentaraMangaDto(
    val comic: HentaraComicFullDto,
    val episodes: List<HentaraEpisodeShortDto>,
)

@Serializable
data class HentaraComicFullDto(
    val title: String,
    val slug: String,
    val description: String,
    val thumbnail_url: String,
    val genres: List<HentaraGenreDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manhwa/$slug"
        title = this@HentaraComicFullDto.title
        thumbnail_url = this@HentaraComicFullDto.thumbnail_url
        description = this@HentaraComicFullDto.description
        genre = genres.joinToString { it.name }
    }
}

@Serializable
data class HentaraEpisodeShortDto(
    val episode_number: Int,
    val title: String,
    val created_at: String,
)

@Serializable
data class HentaraEpisodeDto(
    val pages: List<HentaraPageDto>,
)

@Serializable
data class HentaraPageDto(
    val page_number: Int,
    val image_url: String,
)
