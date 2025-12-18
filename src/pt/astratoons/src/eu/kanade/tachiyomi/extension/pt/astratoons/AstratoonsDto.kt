package eu.kanade.tachiyomi.extension.pt.astratoons

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
class ChapterListDto(
    val hasMore: Boolean,
    private val html: String,
) {

    fun asJsoup(baseUri: String = ""): Document {
        return Jsoup.parseBodyFragment(this.html, baseUri)
    }
}

@Serializable
class SearchDto(
    val title: String,
    val slug: String,
    @SerialName("cover_image")
    val thumbnail: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = this@SearchDto.title
        thumbnail_url = "$baseUrl/$thumbnail"
        url = "/comics/$slug"
    }
}
