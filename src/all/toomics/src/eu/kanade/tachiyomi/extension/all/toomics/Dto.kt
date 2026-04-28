package eu.kanade.tachiyomi.extension.all.toomics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchDto(
    @SerialName("webtoon") private val html: Html,
) {
    val content: String get() = html.data
}

@Serializable
class Html(
    @SerialName("sHtml") val data: String,
)
