package eu.kanade.tachiyomi.extension.es.manhwases
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwasEs : Madara(
    "Manhwas.es",
    "https://manhwas.es",
    "es",
    dateFormat = SimpleDateFormat("MMM dd, yy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
}
