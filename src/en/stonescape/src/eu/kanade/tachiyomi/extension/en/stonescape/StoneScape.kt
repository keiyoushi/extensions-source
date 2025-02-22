package eu.kanade.tachiyomi.extension.en.stonescape
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape : Madara(
    "StoneScape",
    "https://stonescape.xyz",
    "en",
    SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
) {
    override val mangaSubString = "series"

    override val chapterUrlSelector = "li > a"

    override val mangaDetailsSelectorAuthor = ".post-content .manga-authors a"

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
