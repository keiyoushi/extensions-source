package eu.kanade.tachiyomi.extension.en.shojoscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper

class VioletScans : MangaThemesiaAlt(
    "Violet Scans",
    "https://violetscans.com",
    "en",
    mangaUrlDirectory = "/comics",
) {
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    override fun chapterListSelector(): String {
        return paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
            super.chapterListSelector(),
            preferences,
        )
    }

    override val id = 9079184529211162476
}
