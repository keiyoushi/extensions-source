package eu.kanade.tachiyomi.extension.en.razure

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Razure : MangaThemesia() {
    override val mangaUrlDirectory = "/series"
    override fun chapterListSelector() = "#chapterlist li:not([data-num*='🔒'])"

    override fun searchMangaSelector() = ".listupd .bs .bsx:not(:has(.novelabel))"
}
