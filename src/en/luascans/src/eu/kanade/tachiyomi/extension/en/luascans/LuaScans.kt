package eu.kanade.tachiyomi.extension.en.luascans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class LuaScans : HeanCms() {

    override val useNewChapterEndpoint = true

    override val latestSortBy = "asc"

    override val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}
