package eu.kanade.tachiyomi.extension.en.manhuafastnet
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaFastNet : Madara("ManhuaFast.net (unoriginal)", "https://manhuafast.net", "en") {
    override val useNewChapterEndpoint = true
}
