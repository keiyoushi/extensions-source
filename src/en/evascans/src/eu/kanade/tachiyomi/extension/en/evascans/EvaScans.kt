package eu.kanade.tachiyomi.extension.en.evascans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import org.jsoup.nodes.Element

@Source
abstract class EvaScans :
    MangaThemesia(),
    ConfigurableSource {
    override val mangaUrlDirectory = "/series"

    override val seriesAltNameSelector = ".desktop-titles"

    private val preferences by getPreferencesLazy()
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(
        lockedChapterSelector = "a[data-bs-target], a[data-coin], .locked-badge",
    )

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

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

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
