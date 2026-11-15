package eu.kanade.tachiyomi.extension.en.mangarawclub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("results_html") private val resultsHtml: String,
    private val page: Int,
    @SerialName("num_pages") private val numPages: Int,
) {
    val html get() = resultsHtml
    val hasNextPage get() = page < numPages
}
