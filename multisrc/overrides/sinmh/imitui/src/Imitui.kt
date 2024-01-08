package eu.kanade.tachiyomi.extension.zh.imitui

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class Imitui : SinMH("爱米推漫画", "https://www.imitui.com") {

    override fun chapterListSelector() = ".chapter-body li > a:not([href^=/comic/app/])"

    override fun pageListParse(document: Document): List<Page> =
        document.select("img[onclick]").mapIndexed { index, img ->
            val url = img.attr("data-src").ifEmpty { img.attr("src") }
            Page(index, imageUrl = url)
        }
}
