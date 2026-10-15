package eu.kanade.tachiyomi.extension.en.mangaforfreecom

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Mangaforfreecom : Madara() {
    override val chapterMode = ChapterMode.AdminAjax
}
