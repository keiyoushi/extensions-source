package eu.kanade.tachiyomi.extension.en.theexiledheavyknightmangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class TheExiledHeavyKnightMangaOnline : SoloManga() {

    override val mangaTitle = "The Exiled Heavy Knight"

    override val chapterListSelector = "ul.scroll-sm a"

    override val pageSelector = ".post-single-content img, .entry-content img"

    override val imageHosts = listOf(
        "pic.readkakegurui.com",
        "img.theexiledheavyknight.com",
        "theexiledheavyknight.com/wp-content/uploads",
    )

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        name = element.selectFirst("span")!!.text()
    }
}
