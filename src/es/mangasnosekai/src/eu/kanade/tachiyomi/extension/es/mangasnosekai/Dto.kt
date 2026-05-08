package eu.kanade.tachiyomi.extension.es.mangasnosekai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterWrapper(
    @SerialName("chapters_to_display") val chapters: List<Chapter>,
    @SerialName("total_pages") private val totalPages: Int,
    @SerialName("current_page") private val currentPage: Int,
) {
    fun hasNextPage() = currentPage < totalPages
}

@Serializable
class Chapter(
    val name: String,
    @SerialName("link") val url: String,
    val date: String,
)
