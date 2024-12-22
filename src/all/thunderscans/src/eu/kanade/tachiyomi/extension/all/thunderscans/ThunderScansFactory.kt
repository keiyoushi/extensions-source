package eu.kanade.tachiyomi.extension.all.thunderscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ThunderScansFactory : SourceFactory {
    override fun createSources() = listOf(
        LavaScans(),
        ThunderScans(),
    )
}

abstract class ThunderScansBase(
    name: String,
    baseUrl: String,
    lang: String,
    mangaUrlDirectory: String = "/manga",
    dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) : MangaThemesiaAlt(name, baseUrl, lang, mangaUrlDirectory, dateFormat) {
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

class LavaScans : ThunderScansBase(
    "Lava Scans",
    "https://lavatoons.com",
    "ar",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("ar")),
) {
    override val id = 3209001028102012989
}

class ThunderScans : ThunderScansBase(
    "Thunder Scans",
    "https://en-thunderscans.com",
    "en",
    mangaUrlDirectory = "/comics",
)
