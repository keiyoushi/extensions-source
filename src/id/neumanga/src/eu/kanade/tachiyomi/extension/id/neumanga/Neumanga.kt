package eu.kanade.tachiyomi.extension.id.neumanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.colorlibanime.ColorlibAnime

class Neumanga : ColorlibAnime("Neumanga", "https://neumanga.xyz", "id") {
    override val versionId = 2
}
