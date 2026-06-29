package eu.kanade.tachiyomi.extension.en.yorai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Browse(
    val comics: List<Comic>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class Comic(
    val slug: String,
    val title: String,
    val coverUrl: String,
)

@Serializable
class Description(
    val name: String,
    val content: String,
)

@Serializable
class Tag(
    val name: String,
    val slug: String,
)

@Serializable
class Chapters(
    val slug: String,
    val chapters: List<Chapter>,
    val defaultSource: String,
) {
    @Serializable
    class Chapter(
        val number: Float,
        val title: String,
        @SerialName("source_name")
        val sourceName: String,
    )
}

@Serializable
class ChapterPages(
    val imageUrls: List<String>,
)
