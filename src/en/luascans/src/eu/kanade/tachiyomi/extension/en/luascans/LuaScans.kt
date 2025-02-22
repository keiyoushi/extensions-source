package eu.kanade.tachiyomi.extension.en.luascans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms

class LuaScans : HeanCms(
    "Lua Scans",
    "https://luacomic.org",
    "en",
) {
    // Moved from Keyoapp to HeanCms
    override val versionId = 3

    override val useNewChapterEndpoint = true
}
