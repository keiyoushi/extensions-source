package eu.kanade.tachiyomi.extension.en.luascans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.annotation.Source

@Source
abstract class LuaScans : HeanCms() {

    override val latestSortBy = "asc"
}
