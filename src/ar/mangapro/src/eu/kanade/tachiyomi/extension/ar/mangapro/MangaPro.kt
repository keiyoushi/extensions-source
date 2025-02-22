package eu.kanade.tachiyomi.extension.ar.mangapro
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.iken.Iken

class MangaPro : Iken(
    "Manga Pro",
    "ar",
    "https://promanga.pro",
) {
    override val versionId = 4
}
