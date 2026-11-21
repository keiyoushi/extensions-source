package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import keiyoushi.annotation.Source

@Source
abstract class NhatTruyen : WPComics() {

    override val popularPath = "truyen-tranh-hot"

    override val useChapterListApi = true

    override fun getSortList() = netTruyenSortList()

    override val mangaUrlDirectory = "truyen-tranh"
}
