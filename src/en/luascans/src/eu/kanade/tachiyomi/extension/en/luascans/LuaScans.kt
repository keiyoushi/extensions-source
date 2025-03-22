package eu.kanade.tachiyomi.extension.en.luascans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import java.text.SimpleDateFormat
import java.util.Locale

class LuaScans : HeanCms(
    "Lua Scans",
    "https://luacomic.org",
    "en",
) {
    // Moved from Keyoapp to HeanCms
    override val versionId = 3

    override val useNewChapterEndpoint = true

    override val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
}
