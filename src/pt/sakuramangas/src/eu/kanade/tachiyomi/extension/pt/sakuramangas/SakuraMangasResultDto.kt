package eu.kanade.tachiyomi.extension.pt.sakuramangas

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
class SakuraMangasResultDto(
    val hasMore: Boolean,
    private val html: String,
) {

    fun asJsoup(baseUri: String = ""): Document {
        return Jsoup.parseBodyFragment(this.html, baseUri)
    }
}
