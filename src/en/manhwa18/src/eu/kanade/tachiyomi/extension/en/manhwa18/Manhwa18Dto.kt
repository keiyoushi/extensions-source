package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaListBrowse(
    @SerialName("products") val browseList: MangaList,
)

@Serializable
data class MangaList(
    val current_page: Int,
    val last_page: Int,
    @SerialName("data") val mangaList: List<Manga>,
)

@Serializable
data class MangaDetail(
    @SerialName("product") val manga: Manga,
)

@Serializable
data class Manga(
    val name: String,
    val url_avatar: String,
    val slug: String,
    // raw / sub
    val category_id: Int?,
    val is_end: Int?,
    val desc: String?,
    val episodes: List<Episode>?,
    // genre
    val types: List<Type>?,
    // korea / japan
    val nation: Nation?,
) {
    fun toSManga(): SManga {
        return SManga.create().apply {
            // compatible with old theme
            url = "/manga/$slug"
            title = name
            description = desc?.trim()?.removePrefix("<p>")
                ?.removeSuffix("</p>")?.trim()
            genre = listOfNotNull(
                types?.joinToString { it.name },
                nation?.name,
                category_id?.let { Categories[it] },
            )
                .joinToString()

            status = when (is_end) {
                1 -> SManga.COMPLETED
                0 -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            thumbnail_url = url_avatar
        }
    }
}

@Serializable
data class ChapterDetail(
    val episode: Episode,
)

@Serializable
data class Episode(
    val name: String,
    val slug: String,
    val created_at: String?,
    val servers: List<Images>?,
)

@Serializable
data class Images(
    val images: List<String>,
)

@Serializable
data class Nation(
    val name: String,
)

@Serializable
data class Type(
    val name: String,
)
