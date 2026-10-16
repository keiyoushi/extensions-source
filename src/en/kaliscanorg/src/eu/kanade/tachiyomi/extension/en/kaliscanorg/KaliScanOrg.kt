package eu.kanade.tachiyomi.extension.en.kaliscanorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class KaliScanOrg : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
