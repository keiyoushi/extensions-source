package eu.kanade.tachiyomi.extension.all.comikey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComikeyComic(
    val id: Int,
    val link: String,
    val name: String,
    val author: List<ComikeyAuthor>,
    val artist: List<ComikeyAuthor>,
    val tags: List<ComikeyNameWrapper>,
    val description: String,
    val excerpt: String,
    @SerialName("full_cover") val fullCover: String,
    val e4pid: String,
    val format: Int,
    val uslug: String,
)

@Serializable
data class ComikeyChapterListResponse(
    val status: Int,
    val data: ComikeyChapterListData,
)

@Serializable
data class ComikeyChapterListData(
    val episodes: List<ComikeyEpisode>,
)

@Serializable
data class ComikeyEpisode(
    val id: String,
    val number: String,
    val name: List<ComikeyNameWrapper>,
    val language: String,
    val publishedAt: Long,
    val saleAt: Long?,
)

@Serializable
data class ComikeyLmaoInitData(
    val manifest: String,
)

@Serializable
data class ComikeyNameWrapper(
    val name: String,
)

@Serializable
data class ComikeyAuthor(
    val id: Int,
    val name: String,
)
