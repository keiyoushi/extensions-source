package eu.kanade.tachiyomi.extension.en.ezmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms

class EZmanga : HeanCms(
    "EZmanga",
    "https://ezmanga.org",
    "en",
) {
    // Migrated from Keyoapp to HeanCms
    override val versionId = 3

    override val useNewChapterEndpoint = true
}
