package eu.kanade.tachiyomi.extension.pt.littletyrant

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
class ChapterDto(
    private val `data`: HtmlDto,
) {
    fun isEmpty() = data.html.isBlank()
    fun toJsoup(baseUrl: String): Document = Jsoup.parseBodyFragment(data.html, baseUrl)
}

@Serializable
class HtmlDto(
    val html: String,
)
