package eu.kanade.tachiyomi.extension.tr.koreliscans

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import org.jsoup.nodes.Element

@Source
abstract class KoreliScans :
    MangaThemesia(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(
        lockedChapterSelector = LOCKED_CHAPTER_SELECTOR,
    )

    override fun chapterListSelector(): String = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        // Coin chapters carry their number only in the url
        if (name.none(Char::isDigit)) {
            val number = url.removeSuffix("/").substringAfterLast("-bolum-", "")
            if (number.isNotEmpty()) name = "$name ${number.replace('-', '.')}"
        }
        if (element.selectFirst(LOCKED_CHAPTER_SELECTOR) != null) {
            name = "🔒 $name"
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }

    companion object {
        private const val LOCKED_CHAPTER_SELECTOR = "span[data-mrh-locked]"
    }
}
