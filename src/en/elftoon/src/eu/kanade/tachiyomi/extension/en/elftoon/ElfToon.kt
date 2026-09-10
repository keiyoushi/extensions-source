package eu.kanade.tachiyomi.extension.en.elftoon

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferences

@Source
abstract class ElfToon :
    MangaThemesia(),
    ConfigurableSource {
    private val preferences = getPreferences()
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(lockedChapterSelector = ".gem-price-icon")

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
