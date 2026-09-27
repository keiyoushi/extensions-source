package eu.kanade.tachiyomi.extension.ja.mangakuro

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class PageListResponseDto(
    private val status: Boolean = false,
    private val html: String,
) {
    fun toPageList(chapterUrl: String): List<Page> {
        if (!status) return emptyList()

        return Jsoup.parseBodyFragment(html, chapterUrl)
            .select("div.image_story img")
            .mapIndexed { index, element -> Page(index, imageUrl = element.absUrl("src")) }
    }
}
