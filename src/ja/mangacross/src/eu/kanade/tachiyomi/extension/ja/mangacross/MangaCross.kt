package eu.kanade.tachiyomi.extension.ja.mangacross

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt

// MangaCross became ChampionCross
class MangaCross :
    ComiciViewerAlt(
        "Champion Cross",
        "https://championcross.jp",
        "ja",
        "https://championcross.jp/api",
    ) {
    override val versionId = 2
}
