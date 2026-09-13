package eu.kanade.tachiyomi.extension.en.octopusmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class OctopusManga : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
