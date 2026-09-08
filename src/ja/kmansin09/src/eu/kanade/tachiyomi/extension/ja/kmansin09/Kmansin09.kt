package eu.kanade.tachiyomi.extension.ja.kmansin09

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class Kmansin09 : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
}
