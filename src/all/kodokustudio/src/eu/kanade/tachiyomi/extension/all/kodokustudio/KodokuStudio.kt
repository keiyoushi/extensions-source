package eu.kanade.tachiyomi.extension.all.kodokustudio

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class KodokuStudio : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val mangaSubString = "manhua"
}
