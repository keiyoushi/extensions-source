package eu.kanade.tachiyomi.extension.en.evascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class EvaScans : MangaThemesia() {
    override val mangaUrlDirectory = "/series"

    override val seriesAltNameSelector = ".desktop-titles"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        val a = element.selectFirst("a")
        val isLocked = a?.hasAttr("data-bs-target") == true ||
            a?.hasAttr("data-coin") == true ||
            element.selectFirst(".locked-badge") != null

        if (isLocked) {
            name = "🔒 $name"
            if (url.isBlank()) {
                a?.attr("data-id")?.takeIf { it.isNotBlank() }?.let { id ->
                    setUrlWithoutDomain("/?p=$id")
                }
            }
        }
    }
}
