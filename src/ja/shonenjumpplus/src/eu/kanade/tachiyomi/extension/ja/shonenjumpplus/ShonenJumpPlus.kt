package eu.kanade.tachiyomi.extension.ja.shonenjumpplus

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ShonenJumpPlus : GigaViewer() {
    override val searchMangaNextPageSelector = "a.pager-next"

    override fun getCollections(): List<Collection> = listOf(
        Collection("ジャンプ＋連載一覧", ""),
        Collection("ジャンプ＋読切シリーズ", "oneshot"),
        Collection("連載終了作品", "finished"),
    )
}
