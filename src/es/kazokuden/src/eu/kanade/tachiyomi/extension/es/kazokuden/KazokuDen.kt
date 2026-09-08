package eu.kanade.tachiyomi.extension.es.kazokuden

import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class KazokuDen : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
}
