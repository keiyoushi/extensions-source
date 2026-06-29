package eu.kanade.tachiyomi.extension.en.nexcomic

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferences

class NexComic :
    MangaThemesia("NexComic", "https://nexcomic.com", "en"),
    ConfigurableSource {

    private val preferences: SharedPreferences = getPreferences()

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(
        lockedChapterSelector = ".text-gold",
    )

    override fun chapterListSelector(): String {
        // Default selector is too broad; it picks up comment <li> as chapters
        val base = "#chapterlist li[data-num]"

        return paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
            base,
            preferences,
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
