package eu.kanade.tachiyomi.extension.pt.dreamscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DreamScan : Madara(
    "Dream Scan",
    "https://fairydream.com.br",
    "pt-BR",
    SimpleDateFormat("MMMM d, yyyy", Locale("pt", "BR")),
) {
    override val id: Long = 2058412298484770949

    override val useNewChapterEndpoint = true
}
