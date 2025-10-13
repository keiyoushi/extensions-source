package eu.kanade.tachiyomi.extension.ja.mangacross

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer

// MangaCross became ChampionCross
class MangaCross : ComiciViewer(
    "Champion Cross",
    "https://championcross.jp",
    "ja",
) {
    override val versionId = 2
}
