@file:Suppress("ktlint:standard:package-name")

package eu.kanade.tachiyomi.extension.id.Luvyaa

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaPaidChapterHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.getPreferences
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Luvyaa :
    MangaThemesia(
        "Luvyaa",
        "https://v4.luvyaa.co",
        "id",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US),
    ),
    ConfigurableSource {

    private val preferences: SharedPreferences = getPreferences()

    private val paidChapterHelper = MangaThemesiaPaidChapterHelper(
        lockedChapterSelector = "img[alt='🔒']",
    )

    override fun chapterListSelector() = paidChapterHelper.getChapterListSelectorBasedOnHidePaidChaptersPref(
        super.chapterListSelector(),
        preferences,
    )

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        if (element.selectFirst("img[alt='🔒']") != null) {
            name = "🔒 $name"
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        paidChapterHelper.addHidePaidChaptersPreferenceToScreen(screen, intl)
    }
}
