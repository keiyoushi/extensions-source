package eu.kanade.tachiyomi.extension.ar.yonabar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class YonaBar : Madara() {
    override val mangaSubString = "yaoi"

    override val chapterDateSelector = ".chapter-release-date .timediff"

    override val pageListParseSelector = ".reading-content img:not(#image-0\\.0)"

    override fun parsePages(document: Document): List<Page> {
        // Image hosts the site's reader swaps in
        val hosts = document.select("script:containsData(.wp-manga-chapter-img)")
            .flatMap { hostReplaceRegex.findAll(it.data()) }
            .associate { it.groupValues[1] to it.groupValues[2] }

        return super.parsePages(document).onEach { page ->
            val url = page.imageUrl!!.toHttpUrl()
            hosts[url.host]?.let { page.imageUrl = url.newBuilder().host(it).build().toString() }
        }
    }

    companion object {
        private val hostReplaceRegex = Regex("""src\.replace\("([^"]+)",\s*"([^"]+)"\)""")
    }
}
