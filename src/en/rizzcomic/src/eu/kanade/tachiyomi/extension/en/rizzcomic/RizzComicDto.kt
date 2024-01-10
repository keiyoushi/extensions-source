package eu.kanade.tachiyomi.extension.en.rizzcomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comic(
    val id: Int,
    val title: String,
    @SerialName("image_url") val cover: String? = null,
    @SerialName("long_description") val synopsis: String? = null,
    val status: String? = null,
    val type: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val serialization: String? = null,
    @SerialName("genre_id") val genres: String? = null,
) {
    val slug get() = title.trim().lowercase()
        .replace(slugRegex, "-")
        .replace("-s-", "s-")
        .replace("-ll-", "ll-")

    val genreIds get() = genres?.split(",")?.map(String::trim)

    companion object {
        private val slugRegex = Regex("""[^a-z0-9]+""")
    }
}

@Serializable
data class Chapter(
    @SerialName("chapter_time") val time: String? = null,
    @SerialName("chapter_title") val name: String,
)
