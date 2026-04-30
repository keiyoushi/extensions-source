package eu.kanade.tachiyomi.extension.en.mangarawclub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("results_html") val resultsHtml: String,
    val page: Int,
    @SerialName("num_pages") val numPages: Int,
)
