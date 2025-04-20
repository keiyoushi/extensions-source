package eu.kanade.tachiyomi.extension.en.madarascans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper

class MadaraScans : MangaThemesia(
    "Madara Scans",
    "https://madarascans.com",
    "en",
    mangaUrlDirectory = "/series",
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
}
