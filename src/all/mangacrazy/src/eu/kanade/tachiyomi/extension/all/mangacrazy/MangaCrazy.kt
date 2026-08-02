package eu.kanade.tachiyomi.extension.all.mangacrazy

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class MangaCrazy : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
}
