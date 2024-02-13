package eu.kanade.tachiyomi.multisrc.pizzareader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PizzaResultsDto(
    val comics: List<PizzaComicDto> = emptyList(),
)

@Serializable
data class PizzaResultDto(
    val comic: PizzaComicDto? = null,
)

@Serializable
data class PizzaReaderDto(
    val chapter: PizzaChapterDto? = null,
    val comic: PizzaComicDto? = null,
)

@Serializable
data class PizzaComicDto(
    val artist: String = "",
    val author: String = "",
    val chapters: List<PizzaChapterDto> = emptyList(),
    val description: String = "",
    val genres: List<PizzaGenreDto> = emptyList(),
    @SerialName("last_chapter") val lastChapter: PizzaChapterDto? = null,
    val status: String = "",
    val title: String = "",
    val thumbnail: String = "",
    val url: String = "",
)

@Serializable
data class PizzaGenreDto(
    val name: String = "",
)

@Serializable
data class PizzaChapterDto(
    val chapter: Int? = null,
    @SerialName("full_title") val fullTitle: String = "",
    val pages: List<String> = emptyList(),
    @SerialName("published_on") val publishedOn: String = "",
    val subchapter: Int? = null,
    val teams: List<PizzaTeamDto?> = emptyList(),
    val url: String = "",
)

@Serializable
data class PizzaTeamDto(
    val name: String = "",
)
