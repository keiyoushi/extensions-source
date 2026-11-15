package eu.kanade.tachiyomi.multisrc.pizzareader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PizzaResultsDto(
    val comics: List<PizzaComicDto> = emptyList(),
)

@Serializable
class PizzaResultDto(
    val comic: PizzaComicDto? = null,
)

@Serializable
class PizzaReaderDto(
    val chapter: PizzaChapterDto? = null,
)

@Serializable
class PizzaComicDto(
    val artist: String? = null,
    val author: String? = null,
    val chapters: List<PizzaChapterDto> = emptyList(),
    val description: String? = null,
    val genres: List<PizzaGenreDto> = emptyList(),
    @SerialName("last_chapter") val lastChapter: PizzaChapterDto? = null,
    val status: String? = null,
    val title: String = "",
    val thumbnail: String = "",
    val url: String = "",
)

@Serializable
class PizzaGenreDto(
    val name: String = "",
)

@Serializable
class PizzaChapterDto(
    val chapter: Int? = null,
    @SerialName("full_title") val fullTitle: String = "",
    val pages: List<String> = emptyList(),
    @SerialName("published_on") val publishedOn: String = "",
    val subchapter: Int? = null,
    val teams: List<PizzaTeamDto?> = emptyList(),
    val url: String = "",
)

@Serializable
class PizzaTeamDto(
    val name: String = "",
)
