package eu.kanade.tachiyomi.extension.en.opscans

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class MangaData(
    val id: String,
    val name: String,
    val author: String,
    val info: String,
    val genre1: String,
    val genre2: String,
    val genre3: String,
    val cover: String,
    val chapters: List<Chapter>,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = name
        author = this@MangaData.author
        description = info
        genre = listOf(genre1, genre2, genre3).joinToString()
        thumbnail_url = "https://127.0.0.1/image#$cover"
        initialized = true
    }
}

@Serializable
data class Chapter(
    val id: String,
    val title: String? = "",
    val date: String? = "",
    val number: String,
    val images: List<Image>? = emptyList(),
)

@Serializable
data class Image(
    val source: String,
)

@Serializable
data class ImageResponse(
    val image: String,
)
