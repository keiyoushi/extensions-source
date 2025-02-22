package eu.kanade.tachiyomi.extension.all.comicznetv2
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComiczNetV2 : Madara("Comicz.net v2", "https://v2.comiz.net", "all") {
    override val useNewChapterEndpoint = false
}
