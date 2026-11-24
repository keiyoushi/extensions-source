package eu.kanade.tachiyomi.extension.vi.nettruyenx

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import keiyoushi.annotation.Source

@Source
abstract class NetTruyenX : WPComics() {
    override val popularPath = "truyen-tranh-hot"
    override val useChapterListApi = true
    override fun getSortList() = netTruyenSortList()
    override val mangaUrlDirectory = "truyen-tranh"
}
