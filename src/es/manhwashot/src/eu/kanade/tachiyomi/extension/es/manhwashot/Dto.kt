package eu.kanade.tachiyomi.extension.es.manhwashot

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class ChapterListDto(
    private val dates: Map<String, Long>,
    private val levels: Map<String, String>,
) {
    fun dateOf(number: String) = dates[number]?.times(1000) ?: 0L

    fun levelOf(number: String) = levels[number]
}

@Serializable
class ReaderDto(
    private val images: List<String>,
    val locked: Boolean,
) {
    fun toPages() = images.mapIndexed { index, url -> Page(index, imageUrl = url) }
}
