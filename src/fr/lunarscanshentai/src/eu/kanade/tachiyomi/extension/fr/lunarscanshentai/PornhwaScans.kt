package eu.kanade.tachiyomi.extension.fr.lunarscanshentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class PornhwaScans : MangaThemesia(
    "Pornhwa Scans",
    "https://pornhwascans.fr",
    "fr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH),
) {
    // formally Lunar Scans Hentai
    override val id = 5554585746492602896

    override fun chapterListSelector(): String = "div.chapter-list > a.chapter-item"
}
