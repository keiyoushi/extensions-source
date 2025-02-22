package eu.kanade.tachiyomi.extension.fr.poseidonscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PoseidonScans : Madara(
    "PoseidonScans",
    "https://poseidonscans.fr",
    "fr",
    dateFormat = SimpleDateFormat("dd/mm/yyyy", Locale.FRANCE),
) {
    override val useNewChapterEndpoint = true
}
