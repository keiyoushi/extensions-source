package eu.kanade.tachiyomi.extension.tr.diamondfansub
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DiamondFansub : Madara(
    "DiamondFansub",
    "https://diamondfansub.com",
    "tr",
    SimpleDateFormat("d MMMM", Locale("tr", "TR")),
) {
    override val mangaSubString = "seri"
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorAuthor = ".manga-authors"
    override val mangaDetailsSelectorDescription = ".manga-info"
}
