package eu.kanade.tachiyomi.extension.en.mangafun

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MinifiedMangaDto(
    @SerialName("i") val id: Int,
    @SerialName("n") val name: String,
    @SerialName("t") val thumbnailUrl: String? = null,
    @SerialName("s") val publishedStatus: Int = 0,
    @SerialName("tt") val titleType: Int = 0,
    @SerialName("a") val alias: List<String> = emptyList(),
    @SerialName("g") val genres: List<Int> = emptyList(),
    @SerialName("au") val author: List<String> = emptyList(),
    @SerialName("r") val rank: Int = 999999999,
    @SerialName("ca") val createdAt: Int = 0,
    @SerialName("ua") val updatedAt: Int = 0,
)

@Serializable
data class MangaDto(
    val id: Int,
    val name: String,
    val thumbnailURL: String? = null,
    val publishedStatus: Int = 0,
    val titleType: Int = 0,
    val alias: List<String>,
    val description: String,
    val genres: List<GenreDto>,
    val artist: List<String?>,
    val author: List<String?>,
    val chapters: List<ChapterDto>,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val name: String,
    val publishedAt: String,
)

@Serializable
data class GenreDto(val id: Int, val name: String)

@Serializable
data class NextPagePropsWrapperDto(
    val pageProps: NextPagePropsDto,
)

@Serializable
data class NextPagePropsDto(
    val dehydratedState: DehydratedStateDto,
)

@Serializable
data class DehydratedStateDto(
    val queries: List<QueriesDto>,
)

@Serializable
data class QueriesDto(
    val state: StateDto,
)

@Serializable
data class StateDto(
    val data: JsonElement,
)
