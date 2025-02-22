package eu.kanade.tachiyomi.extension.pt.galinhasamuraiscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class GalinhaSamuraiScan : Madara(
    "Galinha Samurai Scan",
    "https://galinhasamurai.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
