package eu.kanade.tachiyomi.extension.en.violetscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class VioletScans : MangaThemesia() {
    override val mangaUrlDirectory = "/comics"

    override fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx:not(:has(.novelabel)), .listo .bs .bsx:not(:has(.novelabel))"

    override fun chapterListSelector(): String = "#chapterlist li:not(:has(svg))"

    override val seriesAltNameSelector = ".alternative .desktop-titles"
}
