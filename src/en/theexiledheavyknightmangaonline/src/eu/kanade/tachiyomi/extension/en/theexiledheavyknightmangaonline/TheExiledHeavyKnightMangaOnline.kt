package eu.kanade.tachiyomi.extension.en.theexiledheavyknightmangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class TheExiledHeavyKnightMangaOnline : SoloManga() {

    override val mangaTitle = "The Exiled Heavy Knight"

    override val chapterListSelector = "ul.scroll-sm a"

    override val imageHosts = listOf("pic.readkakegurui.com")

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select(".right-content > p.wp-block-paragraph:not(:has(strong))")
            .joinToString("\n\n") { it.text() }
    }

    override fun chapterName(element: Element): String = element.selectFirst("span")!!.text()
}
