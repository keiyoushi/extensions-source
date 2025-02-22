package eu.kanade.tachiyomi.extension.en.itsyourrightmanhua
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ItsYourRightManhua : Madara("Its Your Right Manhua", "https://itsyourightmanhua.com/", "en") {
    override val useNewChapterEndpoint = true
}
