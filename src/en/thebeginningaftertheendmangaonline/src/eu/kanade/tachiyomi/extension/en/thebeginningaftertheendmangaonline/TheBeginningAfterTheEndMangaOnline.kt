package eu.kanade.tachiyomi.extension.en.thebeginningaftertheendmangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import keiyoushi.annotation.Source

@Source
abstract class TheBeginningAfterTheEndMangaOnline : SoloManga() {

    override val mangaTitle = "The Beginning After The End"

    override val chapterListSelector = ".kt-svg-icon-list a"
}
