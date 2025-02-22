package eu.kanade.tachiyomi.extension.en.lhtranslation
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class LHTranslation : Madara("LHTranslation", "https://lhtranslation.net", "en") {
    override val versionId = 2
    override val useNewChapterEndpoint = true
}
