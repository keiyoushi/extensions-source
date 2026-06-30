package eu.kanade.tachiyomi.extension.en.thunderscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class ThunderScans : MangaThemesiaAlt() {
    override val mangaUrlDirectory = "/comics"

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    open val searchMangaTitleSelector = ".bigor .tt, h3 a"

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        title = element.selectFirst(searchMangaTitleSelector)?.text()?.takeIf(String::isNotEmpty) ?: element.selectFirst("a")!!.attr("title")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        if (url.isBlank()) {
            val a = element.selectFirst("a")!!
            url = "#locked-${a.attr("data-id")}"
            name = "🔒 $name"
        }
    }
}
