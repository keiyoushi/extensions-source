package eu.kanade.tachiyomi.extension.vi.roadsteam

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class RoadsTeam : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
}
